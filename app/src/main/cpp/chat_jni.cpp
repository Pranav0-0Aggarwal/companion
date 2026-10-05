#include <jni.h>
#include <unistd.h>

#include <algorithm>
#include <atomic>
#include <cstdint>
#include <cstdio>
#include <mutex>
#include <new>
#include <string>
#include <vector>

#include "jni_util.h"
#include "llama.h"

namespace {

constexpr int CTX = 4096;
constexpr int BATCH = 512;
constexpr int STEPS = 512;
constexpr size_t CAP = 16384;

struct C {
    FILE *f = nullptr;
    llama_model *m = nullptr;
    llama_context *c = nullptr;
    std::vector<llama_token> kv;
    std::atomic<bool> stop{false};
};

void drop(C *h) {
    if (!h) return;
    if (h->c) llama_free(h->c);
    if (h->m) llama_model_free(h->m);
    if (h->f) fclose(h->f);
    delete h;
}

void reset(C *h) {
    llama_memory_clear(llama_get_memory(h->c), true);
    h->kv.clear();
}

bool enc(const llama_vocab *v, const std::string &s, std::vector<llama_token> &t) {
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

struct Sink {
    JNIEnv *e;
    jobject o;
    jmethodID m;

    bool put(const std::string &s) const {
        jstring j = jstr(e, s);
        if (!j) return false;
        const bool go = e->CallBooleanMethod(o, m, j) == JNI_TRUE;
        e->DeleteLocalRef(j);
        if (e->ExceptionCheck()) {
            e->ExceptionClear();
            return false;
        }
        return go;
    }
};

bool gen(C *h, const std::string &prompt, const std::string &g, int max, float temp, const Sink &sink, std::string &out) {
    const llama_vocab *v = llama_model_get_vocab(h->m);
    std::vector<llama_token> t;
    if (!enc(v, prompt, t)) return false;
    const int n = (int)t.size();
    max = std::max(1, std::min({max, STEPS, CTX - n}));

    llama_memory_t mem = llama_get_memory(h->c);
    size_t k = 0;
    while (k < h->kv.size() && k < t.size() && h->kv[k] == t[k]) k++;
    if (k == t.size()) k--;
    if (!llama_memory_seq_rm(mem, 0, (llama_pos)k, -1)) {
        llama_memory_clear(mem, true);
        k = 0;
    }
    h->kv.resize(k);

    for (size_t i = k; i < t.size(); i += BATCH) {
        if (h->stop.load()) {
            reset(h);
            return false;
        }
        const int c = (int)std::min((size_t)BATCH, t.size() - i);
        if (llama_decode(h->c, llama_batch_get_one(t.data() + i, c)) != 0) {
            reset(h);
            return false;
        }
    }
    h->kv = t;

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

    bool ok = true;
    std::string pend;
    for (int i = 0; i < max && !h->stop.load(); i++) {
        llama_token tok = llama_sampler_sample(s, h->c, -1);
        if (llama_vocab_is_eog(v, tok)) break;
        char b[256];
        const int m = llama_token_to_piece(v, tok, b, (int32_t)sizeof b, 0, false);
        if (m < 0 || out.size() + (size_t)m > CAP) {
            ok = false;
            break;
        }
        out.append(b, (size_t)m);
        pend.append(b, (size_t)m);
        const size_t w = whole(pend);
        if (w > 0) {
            const bool go = sink.put(pend.substr(0, w));
            pend.erase(0, w);
            if (!go) break;
        }
        if (i + 1 < max && llama_decode(h->c, llama_batch_get_one(&tok, 1)) != 0) {
            ok = false;
            break;
        }
    }
    llama_sampler_free(s);

    if (!ok || !llama_memory_seq_rm(mem, 0, (llama_pos)t.size(), -1)) {
        reset(h);
        return ok;
    }
    return true;
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
        llama_model_params mp = llama_model_default_params();
        mp.n_gpu_layers = 0;
        mp.load_mode = LLAMA_LOAD_MODE_MMAP;
        h->m = llama_model_load_from_file_ptr(h->f, mp);
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
        cp.no_perf = true;
        h->c = llama_init_from_model(h->m, cp);
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

extern "C" JNIEXPORT jboolean JNICALL Java_app_companion_ai_ChatJni_run(JNIEnv *e, jobject, jlong hh, jstring prompt, jstring grammar, jint maxTokens, jint threads, jfloat temp, jobject sink) {
    C *h = (C *)(intptr_t)hh;
    if (!h || !prompt || !grammar || !sink) return JNI_FALSE;
    jclass cls = e->FindClass("java/util/function/Predicate");
    jmethodID mid = cls ? e->GetMethodID(cls, "test", "(Ljava/lang/Object;)Z") : nullptr;
    if (!mid) {
        e->ExceptionClear();
        return JNI_FALSE;
    }
    h->stop.store(false);
    try {
        const std::string p = utf8(e, prompt);
        const std::string g = utf8(e, grammar);
        if (p.empty()) return JNI_FALSE;
        llama_set_n_threads(h->c, std::max(1, std::min((int)threads, 4)), std::max(1, std::min((int)threads, 4)));
        std::string out;
        return gen(h, p, g, (int)maxTokens, (float)temp, Sink{e, sink, mid}, out) ? JNI_TRUE : JNI_FALSE;
    } catch (...) {
        reset(h);
        return JNI_FALSE;
    }
}

extern "C" JNIEXPORT void JNICALL Java_app_companion_ai_ChatJni_cancel(JNIEnv *, jobject, jlong hh) {
    C *h = (C *)(intptr_t)hh;
    if (h) h->stop.store(true);
}

extern "C" JNIEXPORT void JNICALL Java_app_companion_ai_ChatJni_free(JNIEnv *, jobject, jlong hh) {
    drop((C *)(intptr_t)hh);
}
