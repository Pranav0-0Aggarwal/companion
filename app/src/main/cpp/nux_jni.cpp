#include <jni.h>
#include <unistd.h>

#include <algorithm>
#include <cstdint>
#include <cstdio>
#include <mutex>
#include <new>
#include <string>
#include <vector>

#include "jni_util.h"
#include "llama.h"

namespace {

constexpr int CTX = 768;
constexpr int BATCH = 512;
constexpr int STEPS = 256;
constexpr size_t CAP = 4096;

struct H {
    FILE *f = nullptr;
    llama_model *m = nullptr;
    llama_context *c = nullptr;
    std::vector<llama_token> kv;
};

void drop(H *h) {
    if (!h) return;
    if (h->c) llama_free(h->c);
    if (h->m) llama_model_free(h->m);
    if (h->f) fclose(h->f);
    delete h;
}

void reset(H *h) {
    llama_memory_clear(llama_get_memory(h->c), true);
    h->kv.clear();
}

bool enc(const llama_vocab *v, const std::string &s, bool add, bool special, std::vector<llama_token> &t) {
    if (s.empty()) return true;
    std::vector<llama_token> b(CTX);
    const int n = llama_tokenize(v, s.data(), (int32_t)s.size(), b.data(), (int32_t)b.size(), add, special);
    if (n < 0) return false;
    t.insert(t.end(), b.begin(), b.begin() + n);
    return t.size() < (size_t)(CTX - 8);
}

bool gen(H *h, const std::string &pre, const std::string &txt, const std::string &suf, const std::string &g, int max, std::string &out) {
    const llama_vocab *v = llama_model_get_vocab(h->m);
    std::vector<llama_token> t;
    if (!enc(v, pre + txt + suf, true, true, t)) return false;
    const int n = (int)t.size();
    if (n <= 0) return false;
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
        const int c = (int)std::min((size_t)BATCH, t.size() - i);
        if (llama_decode(h->c, llama_batch_get_one(t.data() + i, c)) != 0) {
            reset(h);
            return false;
        }
    }
    h->kv = t;

    llama_sampler *s = llama_sampler_chain_init(llama_sampler_chain_default_params());
    llama_sampler *gr = s ? llama_sampler_init_grammar(v, g.c_str(), "root") : nullptr;
    if (!gr) {
        if (s) llama_sampler_free(s);
        return false;
    }
    llama_sampler_chain_add(s, gr);
    llama_sampler_chain_add(s, llama_sampler_init_greedy());

    bool ok = true;
    for (int i = 0; i < max; i++) {
        llama_token tok = llama_sampler_sample(s, h->c, -1);
        if (llama_vocab_is_eog(v, tok)) break;
        char b[128];
        const int m = llama_token_to_piece(v, tok, b, (int32_t)sizeof b, 0, false);
        if (m < 0 || out.size() + (size_t)m > CAP) {
            ok = false;
            break;
        }
        out.append(b, (size_t)m);
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

extern "C" JNIEXPORT jlong JNICALL Java_app_companion_ai_NuxJni_load(JNIEnv *, jobject, jint fd, jint threads) {
    if (!capable()) return 0;
    static std::once_flag once;
    std::call_once(once, [] {
        llama_log_set(quiet, nullptr);
        llama_backend_init();
    });
    H *h = new (std::nothrow) H;
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

extern "C" JNIEXPORT jstring JNICALL Java_app_companion_ai_NuxJni_run(JNIEnv *e, jobject, jlong hh, jstring prefix, jstring text, jstring suffix, jstring grammar, jint maxTokens) {
    H *h = (H *)(intptr_t)hh;
    if (!h || !prefix || !text || !suffix || !grammar) return nullptr;
    std::string out;
    try {
        const std::string p = utf8(e, prefix);
        const std::string x = utf8(e, text);
        const std::string f = utf8(e, suffix);
        const std::string g = utf8(e, grammar);
        if (p.empty() || g.empty() || !gen(h, p, x, f, g, (int)maxTokens, out)) return nullptr;
    } catch (...) {
        reset(h);
        return nullptr;
    }
    return jstr(e, out);
}

extern "C" JNIEXPORT void JNICALL Java_app_companion_ai_NuxJni_free(JNIEnv *, jobject, jlong hh) {
    drop((H *)(intptr_t)hh);
}
