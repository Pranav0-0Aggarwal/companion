import hashlib
import json
import random
import sys

from tokenizers import Tokenizer

REV = "45bb4654a4d5aaff24dd11d4781fa46d39bf8c13"
SHA = "9fd55248d51d33976b324fc11592e28071da7d41e0e9401dfb7082e30574b7b1"

FIXED = [
    "", " ", "a", "hello world", "Hello, World!", "  two spaces", "three   spaces", "x" + " " * 23 + "y", "x" + " " * 24 + "y", "x" + " " * 25 + "y",
    " " * 30, "trailing   ", "   leading", "tab\there", "line\nbreak", "crlf\r\nbreak", "\n\n\nmany\n\n", "a \n b", "a b nbsp", "a b emsp",
    "a　b ideographic", "a\u0085b nel", "a​b zwsp", "a b ls", "a\x1cb fs", "a\x0bb vt", "a\x0cb ff", "﻿bom start",
    "It's Ram's, don't, we're, they've, I'm, we'll, he'd", "It’s curly, don’t", "'s 't 're 've 'm 'll 'd", "ITS' 'S 'T", "rock'n'roll", "'quoted'",
    "café café decomposed", "ñ ä ô", "각 hangul jamo", "Å angstrom Ω ohm", "ﬁ ligature ＦＵＬＬ width １２３",
    "ÄÖÜ ß straße", "İstanbul", "ΣΊΣΥΦΟΣ",
    "\U0001f600", "\U0001f44d\U0001f3fd ok", "\U0001f468‍\U0001f469‍\U0001f467 family", "❤️ love", "\U0001f1ee\U0001f1f3 flag", "\U0001f389\U0001f389\U0001f389 party", "⭐⭐⭐⭐⭐",
    "₹", "₹1,299.00", "Rs.500/-", "INR 2,50,000", "$12.50 €9 £3", "Rs 1,23,456.78", "₹₹₹",
    "नमस्ते दुनिया", "आपका OTP 4821 है", "আমি ভালো আছি", "தமிழ் text", "తెలుగు 123",
    "中文测试", "日本語のテキスト", "한국어", "مرحبا بالعالم",
    "https://bit.ly/3xYz", "www.example.co.in/path?a=1&b=2", "mail me at test.user+tag@example.com", "@handle_name hi", "http://192.168.1.1:8080/x#frag",
    "A/c XX1234 debited", "UPI ref 412345678901", "x__y--z", "e-mail co-op well_known", "[P] [L] (x) [DESCRIPTION]",
    "[CLS]", "[SEP]", "[PAD]", "[UNK]", "[MASK]", "hi [MASK]", "hi   [MASK] there", "[MASK][MASK]", "a [CLS] b [SEP] c", "[unused0] [unused12]", "[CLS", "[cls]",
    "|||EMAIL_ADDRESS|||", "|||PHONE_NUMBER|||", "|||IP_ADDRESS|||", "mail |||EMAIL_ADDRESS||| now", "||| |||", "<|endoftext|>", "<|padding|>", "<|endoftext|><|padding|>", "<|endoftext",
    "1234567890", "1234567890123456789012345678901234567890", "1.5kg @ 99.9%", "10:30AM 04/10/2026", "#hashtag *bold* ~strike~", "a,b;c:d", "!!!???...", "-----", "=====>", "a-b_c.d",
    "0", "00", "007", "1,000,000", "3.14159", "1e10", "١٢٣", "½ ² ⅕",
    "A" * 300, "ab" * 200, "word " * 80, ("Rs.100 debited. " * 20), "नमस्ते " * 60, "\U0001f600" * 150,
    "UPPER lower MiXeD CamelCase snake_case kebab-case", "a" * 5 + " " + "b" * 5 + "   " + "c" * 5,
]

SND = ["VM-HDFCBK", "AX-ICICIT", "JD-SBIUPI", "BZ-AMAZON", "VK-SWIGGY", "TM-ZOMATO", "AD-AIRTEL", "JM-PAYTMB", "+919876543210", "Mom", "Rahul Bhai"]
NAMES = ["Priya", "Amit", "Neha", "Rohan", "Kavya", "Arjun", "Sneha", "Vikram"]
SHOPS = ["Swiggy", "Zomato", "BigBasket", "Blinkit", "Uber", "Ola", "IRCTC", "MakeMyTrip", "Flipkart", "Myntra", "Apollo Pharmacy", "BookMyShow"]
TPL = [
    "Rs.{amt} debited from A/c XX{acc} on {d} to VPA {nm}@okicici. UPI Ref {ref}. Not you? Call 1800{acc}",
    "INR {amt} credited to your a/c XX{acc} on {d}. Avl Bal: INR {bal}. -{bank}",
    "{otp} is your OTP for txn of ₹{amt} at {shop}. Valid for 10 mins. Do not share it with anyone.",
    "Your {shop} order #{ref} has been shipped! Track: https://{lk}.in/t/{code} \U0001f4e6",
    "Dear customer, your bill of Rs {amt} for {mob} is due on {d}. Pay now: https://pay.{lk}.com/{code}",
    "\U0001f389 Flat {pct}% OFF on {shop}! Use code {code}. T&C apply. Visit www.{lk}.com",
    "Congratulations!!! You won ₹{bal} lottery. Click http://{lk}.xyz/{code} to claim & update KYC now",
    "Bhai {nm}, kal {t} baje milte hai na? \U0001f602 paise bhej diye ₹{amt}",
    "Arre yaar, aaj {shop} se order kiya, delivery {t} tak aa jayegi \U0001f64f",
    "Your Uber trip with {nm} on {d}: ₹{amt} charged to Paytm wallet. Rate your ride ⭐⭐⭐⭐⭐",
    "ALERT: New login to your {bank} NetBanking from Chrome on {d} {t}.  If not you,   call 1800-{acc}",
    "Mummy ka call aaya tha, ghar jaldi aana. {nm} ko bolna dawai le aaye \U0001f48a",
    "Rs. {amt}.00 spent on {bank} Credit Card ending {acc} at {shop} on {d}. Avl Lmt: Rs. {bal}",
    "Refund of ₹{amt} for order {ref} processed to your {bank} account. It may take 5-7 days.",
    "PNR {ref}: Train 12951 {d}, Coach B{pct}, Berth {acc}. Happy journey! -IRCTC",
    "Kya scene hai {nm}?? weekend pe {shop} chalein? \U0001f355\U0001f354",
]


def fill(rnd, t):
    return t.format(
        amt=f"{rnd.randint(1, 99999):,}.{rnd.randint(0, 99):02d}", acc=rnd.randint(1000, 9999), d=f"{rnd.randint(1, 28):02d}-{rnd.choice(['Jan', 'Feb', 'Mar', 'Oct'])}-26",
        nm=rnd.choice(NAMES), ref=rnd.randint(10**8, 10**12), bal=f"{rnd.randint(100, 9999999):,}", bank=rnd.choice(["HDFC", "ICICI", "SBI", "Axis"]),
        otp=rnd.randint(1000, 999999), shop=rnd.choice(SHOPS), lk=rnd.choice(["bit", "rb", "tiny", "go"]), code=f"{rnd.choice(['AB', 'XZ', 'Q9'])}{rnd.randint(10, 9999)}",
        mob=rnd.randint(7000000000, 9999999999), pct=rnd.randint(5, 80), t=f"{rnd.randint(1, 12)}:{rnd.randint(0, 59):02d}{rnd.choice(['AM', 'PM'])}",
    )


def texts():
    rnd = random.Random(7)
    out = list(FIXED)
    while len(out) < 215:
        out.append(f"{rnd.choice(SND)}: {fill(rnd, rnd.choice(TPL))}")
    return out


def main(path, out):
    sha = hashlib.sha256(open(path, "rb").read()).hexdigest()
    assert sha == SHA, sha
    tok = Tokenizer.from_file(path)
    rows = []
    for t in texts():
        tok.no_truncation()
        ids = tok.encode(t, add_special_tokens=False).ids
        tok.enable_truncation(max_length=128)
        enc = tok.encode(t).ids
        rows.append({"text": t, "ids": ids, "enc": enc})
    json.dump({"revision": REV, "tokenizer_sha256": SHA, "vectors": rows}, open(out, "w"), ensure_ascii=True, separators=(",", ":"))
    print(len(rows), sum(len(r["ids"]) > 126 for r in rows))


main(sys.argv[1], sys.argv[2])
