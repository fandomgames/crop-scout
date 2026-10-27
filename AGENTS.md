## Crop Scout registry — agents in scope
| Agent | Purpose | Owner | Can reach / touch |
| --- | --- | --- | --- |
| Crop Scout verdict | Structured tray verdict with evidence, grower confirms before any "act" | Farrah | Tray photos, Meta Model API, local verdict log |
| Coding agent (Muse Code) | Build Crop Scout code in small slices, one at a time | Farrah | Repo files only — no keys, no install without Farrah |
| _Unclaimed — fill in if you spot one_ | | | |

Risk tier: Crop Scout verdict = medium risk. Revisit the tier whenever something goes wrong.

## Crop Scout thresholds — do not cross
| Threshold | Monitor | Response |
| --- | --- | --- |
| Never auto-apply a verdict without grower confirmation | Actions lacking an approval record are reported | Hold as "watch", route to grower |
| Never send a raw grower photo to a cloud model without consent | Alert on any external recipient in outbound activity | Pause sharing, notify owner |
| Never let the coding agent merge auth/payment changes without human review | PRs touching auth/payments are flagged | Human review required, no auto-merge |
| Daily model-spend cap, alert at 80% | Daily spend tracked per verdict | Pause auto-verdicts, check pipeline |

Kill switch: one flag disables the verdict feature and falls back to manual entry. Two consecutive low-confidence verdicts pause auto-verdicts and trigger a pipeline check.

## Crop Scout scope — hackathon Oct 26–Nov 4
In: one hands-only tray scan on Quest 3; SAM 3.1 segmentation;
Muse Spark structured verdict (healthy / watch / act / unknown)
with evidence and confidence; grower confirms/edits/declines
before any "act"; verdict stored with image hash, mask hash,
and model/prompt version.
Out: no auto-applied actions, no controllers, no Jetpack port,
no Ember & Ash, no store submission, no second features.
Success: one in-headset demo, real tray, real verdict,
scan in the first 35 seconds.
