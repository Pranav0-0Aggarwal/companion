#pragma once

#include <jni.h>
#include <sys/auxv.h>

#include <cstdint>
#include <string>

#include "llama.h"

#if defined(__aarch64__)
#include <asm/hwcap.h>
#endif

namespace {

void quiet(ggml_log_level, const char *, void *) {}

bool capable() {
#if defined(__aarch64__)
    return (getauxval(AT_HWCAP) & HWCAP_ASIMDDP) && (getauxval(AT_HWCAP2) & HWCAP2_I8MM);
#else
    return false;
#endif
}

std::string utf8(JNIEnv *e, jstring s) {
    std::string o;
    const jsize n = e->GetStringLength(s);
    const jchar *p = e->GetStringChars(s, nullptr);
    if (!p) return o;
    for (jsize i = 0; i < n; i++) {
        uint32_t c = p[i];
        if (c >= 0xD800 && c < 0xDC00 && i + 1 < n && p[i + 1] >= 0xDC00 && p[i + 1] < 0xE000) {
            c = 0x10000 + ((c - 0xD800) << 10) + (p[++i] - 0xDC00);
        } else if (c >= 0xD800 && c < 0xE000) {
            c = 0xFFFD;
        }
        if (c < 0x80) {
            o += (char)c;
        } else if (c < 0x800) {
            o += (char)(0xC0 | (c >> 6));
            o += (char)(0x80 | (c & 0x3F));
        } else if (c < 0x10000) {
            o += (char)(0xE0 | (c >> 12));
            o += (char)(0x80 | ((c >> 6) & 0x3F));
            o += (char)(0x80 | (c & 0x3F));
        } else {
            o += (char)(0xF0 | (c >> 18));
            o += (char)(0x80 | ((c >> 12) & 0x3F));
            o += (char)(0x80 | ((c >> 6) & 0x3F));
            o += (char)(0x80 | (c & 0x3F));
        }
    }
    e->ReleaseStringChars(s, p);
    return o;
}

jstring jstr(JNIEnv *e, const std::string &s) {
    std::u16string u;
    size_t i = 0;
    while (i < s.size()) {
        const unsigned char b = (unsigned char)s[i++];
        uint32_t c = 0xFFFD;
        int k = 0;
        if (b < 0x80) c = b;
        else if ((b & 0xE0) == 0xC0) { c = b & 0x1F; k = 1; }
        else if ((b & 0xF0) == 0xE0) { c = b & 0x0F; k = 2; }
        else if ((b & 0xF8) == 0xF0) { c = b & 0x07; k = 3; }
        for (int j = 0; j < k; j++) {
            if (i < s.size() && ((unsigned char)s[i] & 0xC0) == 0x80) {
                c = (c << 6) | ((unsigned char)s[i++] & 0x3F);
            } else {
                c = 0xFFFD;
                break;
            }
        }
        if (c > 0x10FFFF || (c >= 0xD800 && c < 0xE000)) c = 0xFFFD;
        if (c >= 0x10000) {
            c -= 0x10000;
            u += (char16_t)(0xD800 + (c >> 10));
            u += (char16_t)(0xDC00 + (c & 0x3FF));
        } else {
            u += (char16_t)c;
        }
    }
    return e->NewString((const jchar *)u.data(), (jsize)u.size());
}

}
