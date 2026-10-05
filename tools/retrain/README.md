# Retrain loop

How owner corrections turn into a better message classifier. There are no scripts and no data here: the training data is private and lives outside the repo at `~/Downloads/arcadebench-models/private-sms`.

## 1. Pull corrections

On the phone: Settings, Export corrections, then save the file or send it to the Mac. Or:

```
adb pull /sdcard/Download/companion-corrections.jsonl
```

Each line is `{"sender", "text", "task", "model", "modelProb", "chosen"}`, oldest first. `task` is `type` or `category`. `model` and `modelProb` are the classifier guess and its calibrated probability, or null when the model made no guess. Every export holds all corrections, so de-duplicate on (`sender`, `text`, `task`) and keep the last line.

## 2. Merge into the private training set

Add the de-duplicated lines to `~/Downloads/arcadebench-models/private-sms`, mapping `chosen` to the training label of that task. Corrections override older labels for the same message. Keep a held-out slice that never receives corrections, for step 5.

## 3. Re-fine-tune ModernBERT

Fine-tune from the current ModernBERT checkpoint on the merged set, with the same label order as `model_spec.json`. Record the dataset size and held-out score.

## 4. Re-export

Export `type.tflite`, `category.tflite` and `model_spec.json` (and the tokenizer if it changed). Check the label order against the previous export.

## 5. Recalibrate

On the held-out slice, take the raw logits per task and fit one temperature per task (minimise negative log likelihood of `softmax(logits / T)`). Then for each label choose the lowest `sure` bar whose precision on that slice meets the target (0.97 or better), and write `calibration.json`:

```
{ "version": 1, "tasks": { "type": { "temperature": T, "labels": [...], "sure": { "<label>": bar } }, "category": { ... } } }
```

`labels` must match the model's label order. Corrections with `modelProb` set are also a live check: if the corrected share of high-probability guesses is above the target, tighten the bars.

## 6. Publish

Attach `model_spec.json`, `type.tflite`, `category.tflite`, `tokenizer.json` and `calibration.json` to a new public GitHub release tag, then update the tag, sizes and SHA-256 values in `Manifest` (`app/src/main/kotlin/app/companion/ai/Models.kt`) and ship an app update. The phone downloads exactly those pinned files over Wi-Fi; there is no on-device import or override. The new hashes change the processing key, so Companion offers to reprocess.
