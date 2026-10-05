#include <jni.h>
#include <unistd.h>

#include <algorithm>
#include <atomic>
#include <chrono>
#include <cstdint>
#include <cstdio>
#include <functional>
#include <mutex>
#include <new>
#include <string>
#include <vector>

#include "ggml-backend-impl.h"
#include "jni_util.h"
#include "llama.h"

namespace {

constexpr int CTX = 3072;
constexpr int BATCH = 256;
constexpr int STEPS = 512;
constexpr size_t CAP = 16384;

using Put = std::function<bool(const std::string &)>;
using Tokens = std::vector<llama_token>;

struct C {
    FILE *f = nullptr;
    llama_model *m = nullptr;
    llama_context *c = nullptr;
    Tokens kv, pin;
    std::vector<uint8_t> snap;
    std::atomic<bool> stop{false};
    int64_t st[4] = {0, 0, 0, 0};
};

int64_t since(std::chrono::steady_clock::time_point t) {
    return std::chrono::duration_cast<std::chrono::milliseconds>(std::chrono::steady_clock::now() - t).count();
}

void drop(C *h) {
    if (!h) return;
    if (h->c) llama_free(h->c);
    if (h->m) llama_model_free(h->m);
    if (h->f) fclose(h->f);
    delete h;
}

void clear(C *h) {
    llama_memory_clear(llama_get_memory(h->c), true);
    h->kv.clear();
}

bool enc(const llama_vocab *v, const std::string &s, Tokens &t) {
    t.resize(s.size() + 16);
    int n = llama_tokenize(v, s.data(), (int32_t)s.size(), t.data(), (int32_t)t.size(), true, true);
    if (n < 0) {
        t.resize((size_t)-n);
        n = llama_tokenize(v, s.data(), (int32_t)s.size(), t.data(), (int32_t)t.size(), true, true);
    }
    if (n <= 0) return false;
    t.resize((size_t)n);
    return t.size() < (size_t)(CTX - 16);
}

size_t same(const Tokens &a, const Tokens &b) {
    size_t i = 0;
    while (i < a.size() && i < b.size() && a[i] == b[i]) i++;
    return i;
}

bool eval(C *h, Tokens &t) {
    for (size_t i = h->kv.size(); i < t.size(); i += BATCH) {
        if (h->stop.load()) return false;
        const size_t c = std::min((size_t)BATCH, t.size() - i);
        if (llama_decode(h->c, llama_batch_get_one(t.data() + i, (int32_t)c)) != 0) {
            clear(h);
            return false;
        }
        h->kv.insert(h->kv.end(), t.begin() + (long)i, t.begin() + (long)(i + c));
        h->st[0] += (int64_t)c;
    }
    return true;
}

bool save(C *h) {
    h->snap.resize(llama_state_seq_get_size(h->c, 0));
    if (llama_state_seq_get_data(h->c, h->snap.data(), h->snap.size(), 0) != h->snap.size()) {
        h->snap.clear();
        return false;
    }
    h->pin = h->kv;
    return true;
}

bool recall(C *h) {
    clear(h);
    if (!h->snap.empty() && llama_state_seq_set_data(h->c, h->snap.data(), h->snap.size(), 0) == h->snap.size()) {
        h->kv = h->pin;
        return true;
    }
    clear(h);
    h->snap.clear();
    return false;
}

bool seed(C *h, Tokens &p) {
    if (!h->snap.empty() && h->pin == p) return true;
    h->snap.clear();
    if (same(h->kv, p) != h->kv.size()) clear(h);
    return eval(h, p) && save(h);
}

llama_model *model(FILE *f) {
    static ggml_backend_buffer_type plain = *ggml_backend_cpu_buffer_type();
    static const llama_model_tensor_buft_override tied[] = {{"^token_embd\\.weight$", &plain}, {nullptr, nullptr}};
    llama_model_params mp = llama_model_default_params();
    mp.n_gpu_layers = 0;
    mp.load_mode = LLAMA_LOAD_MODE_NONE;
    mp.tensor_buft_overrides = tied;
    llama_model *m = llama_model_load_from_file_ptr(f, mp);
    if (m) return m;
    rewind(f);
    mp.tensor_buft_overrides = nullptr;
    return llama_model_load_from_file_ptr(f, mp);
}

size_t whole(const std::string &s) {
    size_t i = s.size();
    for (int k = 0; k < 4 && i > 0; k++) {
        const unsigned char b = (unsigned char)s[i - 1];
        i--;
        if ((b & 0xC0) == 0x80) continue;
        const size_t need = b >= 0xF0 ? 4 : b >= 0xE0 ? 3 : b >= 0xC0 ? 2 : 1;
        return s.size() - i >= need ? s.size() : i;
    }
    return s.size();
}

bool prime(C *h, const llama_vocab *v, const std::string &pin, Tokens &t, Tokens &p) {
    p.clear();
    if (pin.empty() || !enc(v, pin, p) || p.size() >= t.size() || same(p, t) != p.size()) {
        p.clear();
        return true;
    }
    return seed(h, p);
}

bool fit(C *h, Tokens &t, const Tokens &p) {
    size_t k = same(h->kv, t);
    if (k == t.size()) k--;
    if (k < h->kv.size()) {
        if (k > 0 && llama_memory_seq_rm(llama_get_memory(h->c), 0, (llama_pos)k, -1)) {
            h->kv.resize(k);
        } else if (p.empty() || !recall(h)) {
            clear(h);
        }
    }
    return eval(h, t);
}

bool gen(C *h, const std::string &prompt, const std::string &pin, const std::string &g, int max, float temp, const Put &put) {
    const auto t0 = std::chrono::steady_clock::now();
    h->st[0] = h->st[1] = h->st[2] = h->st[3] = 0;
    const llama_vocab *v = llama_model_get_vocab(h->m);
    Tokens t, p;
    if (!enc(v, prompt, t) || !prime(h, v, pin, t, p) || !fit(h, t, p)) return false;
    h->st[1] = since(t0);
    max = std::max(1, std::min({max, STEPS, CTX - (int)t.size()}));

    llama_sampler *s = llama_sampler_chain_init(llama_sampler_chain_default_params());
    if (!s) return false;
    if (!g.empty()) {
        llama_sampler *gr = llama_sampler_init_grammar(v, g.c_str(), "root");
        if (!gr) {
            llama_sampler_free(s);
            return false;
        }
        llama_sampler_chain_add(s, gr);
    }
    if (temp > 0.0f) {
        llama_sampler_chain_add(s, llama_sampler_init_top_k(40));
        llama_sampler_chain_add(s, llama_sampler_init_temp(temp));
        llama_sampler_chain_add(s, llama_sampler_init_dist(LLAMA_DEFAULT_SEED));
    } else {
        llama_sampler_chain_add(s, llama_sampler_init_greedy());
    }

    const auto t1 = std::chrono::steady_clock::now();
    bool ok = true;
    size_t size = 0;
    std::string pend;
    for (int i = 0; i < max && !h->stop.load(); i++) {
        llama_token tok = llama_sampler_sample(s, h->c, -1);
        if (llama_vocab_is_eog(v, tok)) break;
        h->st[2]++;
        char b[256];
        const int m = llama_token_to_piece(v, tok, b, (int32_t)sizeof b, 0, false);
        if (m < 0 || size + (size_t)m > CAP) {
            ok = false;
            break;
        }
        size += (size_t)m;
        pend.append(b, (size_t)m);
        const size_t w = whole(pend);
        if (w > 0) {
            const bool go = put(pend.substr(0, w));
            pend.erase(0, w);
            if (!go) break;
        }
        if (i + 1 < max) {
            if (llama_decode(h->c, llama_batch_get_one(&tok, 1)) != 0) {
                ok = false;
                break;
            }
            h->kv.push_back(tok);
        }
    }
    llama_sampler_free(s);
    h->st[3] = since(t1);
    if (!ok) clear(h);
    return ok;
}

bool warm(C *h, const std::string &pin) {
    h->st[0] = h->st[1] = h->st[2] = h->st[3] = 0;
    const auto t0 = std::chrono::steady_clock::now();
    Tokens p;
    const bool ok = !pin.empty() && enc(llama_model_get_vocab(h->m), pin, p) && seed(h, p);
    h->st[1] = since(t0);
    return ok;
}

}

extern "C" JNIEXPORT jlong JNICALL Java_app_companion_ai_ChatJni_load(JNIEnv *, jobject, jint fd, jint threads) {
    if (!capable()) return 0;
    static std::once_flag once;
    std::call_once(once, [] {
        llama_log_set(quiet, nullptr);
        llama_backend_init();
    });
    C *h = new (std::nothrow) C;
    if (!h) return 0;
    try {
        const int d = dup(fd);
        if (d < 0) {
            drop(h);
            return 0;
        }
        h->f = fdopen(d, "rb");
        if (!h->f) {
            close(d);
            drop(h);
            return 0;
        }
        rewind(h->f);
        h->m = model(h->f);
        if (!h->m) {
            drop(h);
            return 0;
        }
        llama_context_params cp = llama_context_default_params();
        cp.n_ctx = CTX;
        cp.n_batch = BATCH;
        cp.n_ubatch = BATCH;
        cp.n_seq_max = 1;
        cp.n_threads = cp.n_threads_batch = std::max(1, std::min((int)threads, 4));
        cp.type_k = cp.type_v = GGML_TYPE_Q8_0;
        cp.flash_attn_type = LLAMA_FLASH_ATTN_TYPE_ENABLED;
        cp.no_perf = true;
        h->c = llama_init_from_model(h->m, cp);
        if (!h->c) {
            cp.type_k = cp.type_v = GGML_TYPE_F16;
            cp.flash_attn_type = LLAMA_FLASH_ATTN_TYPE_AUTO;
            h->c = llama_init_from_model(h->m, cp);
        }
        if (!h->c) {
            drop(h);
            return 0;
        }
        return (jlong)(intptr_t)h;
    } catch (...) {
        drop(h);
        return 0;
    }
}

extern "C" JNIEXPORT jboolean JNICALL Java_app_companion_ai_ChatJni_run(JNIEnv *e, jobject, jlong hh, jstring prompt, jstring pin, jstring grammar, jint maxTokens, jint threads, jint pre, jfloat temp, jobject sink) {
    C *h = (C *)(intptr_t)hh;
    if (!h || !prompt || !pin || !grammar || !sink) return JNI_FALSE;
    jclass cls = e->FindClass("java/util/function/Predicate");
    jmethodID mid = cls ? e->GetMethodID(cls, "test", "(Ljava/lang/Object;)Z") : nullptr;
    if (!mid) {
        e->ExceptionClear();
        return JNI_FALSE;
    }
    h->stop.store(false);
    try {
        const std::string p = utf8(e, prompt);
        if (p.empty()) return JNI_FALSE;
        llama_set_n_threads(h->c, std::max(1, std::min((int)threads, 4)), std::max(1, std::min((int)pre, 4)));
        const Put put = [&](const std::string &s) {
            jstring j = jstr(e, s);
            if (!j) return false;
            const bool go = e->CallBooleanMethod(sink, mid, j) == JNI_TRUE;
            e->DeleteLocalRef(j);
            if (e->ExceptionCheck()) {
                e->ExceptionClear();
                return false;
            }
            return go;
        };
        return gen(h, p, utf8(e, pin), utf8(e, grammar), (int)maxTokens, (float)temp, put) ? JNI_TRUE : JNI_FALSE;
    } catch (...) {
        clear(h);
        return JNI_FALSE;
    }
}

extern "C" JNIEXPORT jboolean JNICALL Java_app_companion_ai_ChatJni_warm(JNIEnv *e, jobject, jlong hh, jstring pin, jint pre) {
    C *h = (C *)(intptr_t)hh;
    if (!h || !pin) return JNI_FALSE;
    h->stop.store(false);
    try {
        llama_set_n_threads(h->c, 1, std::max(1, std::min((int)pre, 4)));
        return warm(h, utf8(e, pin)) ? JNI_TRUE : JNI_FALSE;
    } catch (...) {
        clear(h);
        return JNI_FALSE;
    }
}

extern "C" JNIEXPORT jlongArray JNICALL Java_app_companion_ai_ChatJni_stats(JNIEnv *e, jobject, jlong hh) {
    C *h = (C *)(intptr_t)hh;
    jlongArray a = e->NewLongArray(4);
    if (a && h) e->SetLongArrayRegion(a, 0, 4, (const jlong *)h->st);
    return a;
}

extern "C" JNIEXPORT void JNICALL Java_app_companion_ai_ChatJni_cancel(JNIEnv *, jobject, jlong hh) {
    C *h = (C *)(intptr_t)hh;
    if (h) h->stop.store(true);
}

extern "C" JNIEXPORT void JNICALL Java_app_companion_ai_ChatJni_free(JNIEnv *, jobject, jlong hh) {
    drop((C *)(intptr_t)hh);
}
