# Retrain loop

How owner corrections turn into a better Decide model. There are no scripts and no data here: the training data is private and lives outside the repo at `~/Downloads/arcadebench-models/private-sms`.

## 1. Pull corrections

On the phone: Settings, Export corrections, then save the file or send it to the Mac. Or:

```
adb pull /sdcard/Download/companion-corrections.jsonl
```

Each line is `{"sender", "text", "task", "model", "modelProb", "chosen"}`, oldest first. `task` is `type` or `category`. `model` and `modelProb` are the Decide guess and its calibrated probability, or null when the model made no guess. Every export holds all corrections, so de-duplicate on (`sender`, `text`, `task`) and keep the last line.

## 2. Merge into the private training set

Add the de-duplicated lines to `~/Downloads/arcadebench-models/private-sms`, mapping `chosen` to the training label of that task. Corrections override older labels for the same message. Keep a held-out slice that never receives corrections, for step 5.

## 3. Re-fine-tune GLiNER

Fine-tune from the current GLiNER checkpoint on the merged set, with the same label order as `models/schema_prefix.json`. Record the dataset size and held-out score.

## 4. Re-export via tools/decide

Run the export in `tools/decide` to produce `decide.tflite`, `schema_prefix.json` and the tokenizer. Check the label order against the previous export.

## 5. Recalibrate

On the held-out slice, take the raw logits per task and fit one temperature per task (minimise negative log likelihood of `softmax(logits / T)`). Then for each label choose the lowest `sure` bar whose precision on that slice meets the target (0.97 or better), and write `calibration.json`:

```
{ "version": 1, "tasks": { "type": { "temperature": T, "labels": [...], "sure": { "<label>": bar } }, "category": { ... } } }
```

`labels` must match the model's label order. Corrections with `modelProb` set are also a live check: if the corrected share of high-probability guesses is above the target, tighten the bars.

## 6. Push

Private models go in `files/models/custom/` with a `custom.json` of their SHA-256s and win over the downloaded base models. No rebuild is needed.

```
shasum -a 256 decide.tflite calibration.json
echo '{"decide.tflite": "<sha256>", "calibration.json": "<sha256>"}' > custom.json
adb push decide.tflite calibration.json custom.json /data/local/tmp/
adb shell run-as app.companion sh -c 'mkdir -p files/models/custom && cp /data/local/tmp/decide.tflite /data/local/tmp/calibration.json /data/local/tmp/custom.json files/models/custom/'
```

Settings, On-device AI then shows both as custom, and Corrections shows the new Decide version. A file that does not match its `custom.json` hash is ignored and the base file is used.
