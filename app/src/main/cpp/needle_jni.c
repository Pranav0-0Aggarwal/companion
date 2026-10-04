#include <jni.h>
#include <stdlib.h>
#include <string.h>
#include <sys/mman.h>
#include <sys/stat.h>
#include <unistd.h>
#include "needle.h"

#define OUT_CAP 65536

typedef struct {
    void *map;
    size_t len;
    int mapped;
    char *sys;
    char *tools;
} H;

static void *slurp(int fd, size_t n) {
    char *b = malloc(n);
    size_t at = 0;
    while (b && at < n) {
        ssize_t r = pread(fd, b + at, n - at, (off_t)at);
        if (r <= 0) {
            free(b);
            return NULL;
        }
        at += (size_t)r;
    }
    return b;
}

static void drop(void *m, size_t n, int mapped) {
    if (mapped) munmap(m, n);
    else free(m);
}

static char *cstr(JNIEnv *e, jbyteArray a) {
    jsize n = (*e)->GetArrayLength(e, a);
    char *s = malloc((size_t)n + 1);
    if (!s) return NULL;
    (*e)->GetByteArrayRegion(e, a, 0, n, (jbyte *)s);
    s[n] = 0;
    return s;
}

JNIEXPORT jlong JNICALL Java_app_companion_ai_NeedleJni_load(JNIEnv *e, jobject o, jint fd) {
    (void)e;
    (void)o;
    setenv("NEEDLE_TELEMETRY", "0", 1);
    setenv("DO_NOT_TRACK", "1", 1);
    struct stat st;
    if (fstat(fd, &st) != 0 || st.st_size <= 0) return 0;
    size_t n = (size_t)st.st_size;
    void *m = mmap(NULL, n, PROT_READ | PROT_WRITE, MAP_PRIVATE, fd, 0);
    int mapped = m != MAP_FAILED;
    if (!mapped) m = slurp(fd, n);
    if (!m) return 0;
    H *h = calloc(1, sizeof(H));
    if (!h || needle_load((const unsigned char *)m, (unsigned long long)n) < 0) {
        free(h);
        drop(m, n, mapped);
        return 0;
    }
    h->map = m;
    h->len = n;
    h->mapped = mapped;
    return (jlong)(intptr_t)h;
}

JNIEXPORT jbyteArray JNICALL Java_app_companion_ai_NeedleJni_exec(JNIEnv *e, jobject o, jlong hh, jbyteArray q, jbyteArray t, jbyteArray s) {
    (void)o;
    H *h = (H *)(intptr_t)hh;
    if (!h) return NULL;
    char *cq = cstr(e, q), *ct = cstr(e, t), *cs = cstr(e, s), *out = malloc(OUT_CAP);
    jbyteArray res = NULL;
    if (cq && ct && cs && out) {
        int ok = 1;
        if (!h->sys || !h->tools || strcmp(h->sys, cs) || strcmp(h->tools, ct)) {
            ok = needle_init(cs, ct, NULL) >= 0;
            free(h->sys);
            free(h->tools);
            h->sys = h->tools = NULL;
            if (ok) {
                h->sys = cs;
                h->tools = ct;
                cs = ct = NULL;
            }
        } else {
            needle_reset();
        }
        int n = ok ? needle_complete(cq, 256, out, OUT_CAP) : -1;
        if (n >= 0) {
            out[OUT_CAP - 1] = 0;
            jsize len = (jsize)strlen(out);
            res = (*e)->NewByteArray(e, len);
            if (res) (*e)->SetByteArrayRegion(e, res, 0, len, (const jbyte *)out);
        }
    }
    free(cq);
    free(ct);
    free(cs);
    free(out);
    return res;
}

JNIEXPORT void JNICALL Java_app_companion_ai_NeedleJni_free(JNIEnv *e, jobject o, jlong hh) {
    (void)e;
    (void)o;
    H *h = (H *)(intptr_t)hh;
    if (!h) return;
    drop(h->map, h->len, h->mapped);
    free(h->sys);
    free(h->tools);
    free(h);
}
