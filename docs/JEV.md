# Jev integration contract

Verified by direct API calls on **2026-09-19**. Everything below was observed, not inferred from
documentation alone. No Jev code exists in the repository yet; this is the contract that integration
must be written against.

## What Jev actually is

`typesafe/jev-1.13` (served as `typesafe/jev-1.13-20260917`) is a **structured decision model** from
TypeSafe, the first of their "System One" models. It was published on 2026-09-18. It does not
generate prose. Its modality is `text -> decisions`: you give it state as text plus a set of typed
questions, and it returns typed answers with calibrated probabilities and a confidence value.

This is exactly the short-term policy layer the project architecture calls for. It is not an LLM and
must not be used as one.

## Finding it

Jev is **absent from OpenRouter's public `/api/v1/models` catalog**. That listing returned 447 models
across 61 providers and contained zero occurrences of `jev` or `typesafe`; decision models appear to
be excluded from it. Do not conclude the model does not exist from that listing. Query it directly:

```bash
curl -s https://openrouter.ai/api/v1/models/typesafe/jev-1.13/endpoints
```

## Endpoint

**`POST https://openrouter.ai/api/alpha/decisions`**

Calling `/api/v1/chat/completions` with this model fails with a clear message, which is how the
correct endpoint was found:

```text
HTTP 400  typesafe/jev-1.13 is a decisions model and cannot be used with the
          chat/completions endpoint. Use the /api/alpha/decisions endpoint instead.
```

Note `alpha` in the path. It is not a stable OpenRouter surface and may move; treat a 404 there as a
signal to re-check rather than as an outage.

TypeSafe also serve this natively at `POST https://api.typesafe.ai/v1/systemone` with a TypeSafe key
and `"model": "jev-latest"`. The request and response bodies are the same shape. We use OpenRouter
because that is the key this project has. Keeping the client's base URL and model id configurable
makes switching a config change rather than a rewrite.

## Request

```json
{
  "model": "typesafe/jev-1.13",
  "state": "<plain text description of the world and the current plan>",
  "questions": {
    "next_action": {
      "type": "choice",
      "instructions": "Choose the next action for the agent.",
      "criteria": {
        "GATHER": "Gather more of the target item",
        "DEPOSIT_ITEM": "Put items into a nearby chest",
        "COMPLETE_TASK": "The current task goal is met; finish it",
        "REQUEST_REPLAN": "Escalate to the planning LLM"
      }
    },
    "risk": {
      "type": "score",
      "instructions": "How risky is it to continue without replanning",
      "criteria": [
        "No risk, state is clean and expected",
        "Some ambiguity worth verifying",
        "High risk, the plan looks invalid"
      ]
    },
    "goal_met": {
      "type": "noul",
      "instructions": "The current gather task's goal is fully satisfied."
    }
  }
}
```

Header: `Authorization: Bearer $OPENROUTER_API_KEY`.

Three question types, and several may be asked in **one** call (TypeSafe call this speculative
fan-out). One round trip answering action, risk and goal at once is the intended usage.

| Type | `criteria` shape | Answer |
| --- | --- | --- |
| `choice` | object of option name to description | `choice`, `probabilities` per option, `confidence` |
| `score` | ordered array of level descriptions | continuous `score`, `legend`, `probabilities`, `confidence` |
| `noul` | none, the question is in `instructions` | `noul`, a probability from 0 to 1 |

`supported_parameters` is empty: there is no temperature, no tools, no `response_format`. The option
set in `criteria` **is** the schema. That is the property this project wants, because the valid
action set becomes unforgeable rather than something a model is asked politely to respect.

## Response

Observed verbatim:

```json
{
  "model": "typesafe/jev-1.13-20260917",
  "answers": {
    "next_action": {
      "type": "choice",
      "choice": "DEPOSIT_ITEM",
      "probabilities": {"DEPOSIT_ITEM": 0.89, "TRAVEL": 0.07, "COMPLETE_TASK": 0.04,
                        "GATHER": 0, "RECOVER": 0, "REQUEST_REPLAN": 0},
      "confidence": 0.86
    },
    "risk": {
      "type": "score", "score": 0.52,
      "legend": {"0": "No risk...", "1": "Some ambiguity...", "2": "High risk..."},
      "probabilities": {"0": 0.57, "1": 0.35, "2": 0.08},
      "confidence": 0.22
    },
    "goal_met": {"type": "noul", "noul": 0.98}
  },
  "usage": {"input_tokens": 608, "output_tokens": 106, "cost": 2.5536e-05},
  "id": "gen-dec-1789810773-xQrZUo0jLMakV6D1vMHl",
  "provider": "TypeSafe"
}
```

## Measured behavior

Four sequential calls, 3 questions each, 608 input tokens of Minecraft state:

| Metric | Value |
| --- | --- |
| Latency | min 0.62 s, median 0.78 s, max 0.92 s |
| First call | 1.98 s, cold start; do not use it as the budget |
| Cost | $0.000026 per call |
| Context | 32,000 tokens, max 28,800 completion |
| Pricing | $0.042 per M input tokens, **$0 output** |

Decision quality on a real scenario was good. Given 32 of 32 logs held, a goal of depositing them, a
chest 8 blocks away and an idle executor, it returned `DEPOSIT_ITEM` at p=0.89 with `goal_met` 0.98.
On a deliberately ambiguous state (31 of 32 logs, executor inactive) it split `GATHER` 0.68 against
`RECOVER` 0.27 and dropped confidence to 0.62, which is the honest answer.

## Consequences for the architecture

**Jev cannot run in the tick loop.** 0.78 s is about 15 Minecraft ticks. It belongs at task
boundaries and decision points, called off-thread, handing a validated decision back to the client
thread. This is the same rule already applied to the LLM, and `GatherController` is already built for
it: it takes observations and a clock and never performs I/O.

**Cost is not a design constraint.** At $0.000026 a call, 780,000 calls fit in $20. The reason to
avoid calling Jev every tick is latency and determinism, not money. Where a deterministic check
answers the question, use the deterministic check: `GatherController` already knows whether the
inventory target is met, and asking a model to confirm arithmetic would be worse in every respect.

**Cost is driven entirely by input tokens**, since output is free. Keep the `state` string compact
and structured. Do not dump raw inventory NBT into it.

**Use `confidence` for escalation.** The brief's "detect when replanning is required" maps directly
onto confidence-gated routing: act on a high-confidence choice, escalate to the LLM planner when
confidence falls below a configured floor. Threshold values are unvalidated so far and must be tuned
against real runs rather than guessed once and forgotten.

**Validate the returned choice anyway.** Treat `choice` as untrusted input and check it against the
action set before dispatch, exactly as `GatherTask` validates its own fields today.

## Credentials

Set `OPENROUTER_API_KEY` in the environment. **Never commit it.** `.gitignore` already excludes
`.env`. No key is stored in this repository, and none should be added to config files that are.

## Sources

- https://openrouter.ai/typesafe/jev-1.13
- https://docs.typesafe.ai/concepts/system-one
- https://docs.typesafe.ai/introduction/quickstart
- https://docs.typesafe.ai/primitives
- https://docs.typesafe.ai/llms.txt
