# Topology JSON Specification

`topology.json` is the authoritative labeled representation of one prefix-network topology. This document is the strict generated-artifact contract for that file.

Use this spec when reviewing generated topology artifacts, writing validators, or deciding whether derived artifacts should be regenerated. Repository workflows and root layouts live in the [prefix-topology-library README](../README.md). The derived shape-only companion format is defined by the [Dyck JSON Specification](dyck-json-spec.md).

## Authority

Every topology directory has one source of truth: `topology.json`. If `dyck.json`, `topology.svg`, generated RTL, prelayout reports, or Pareto summaries disagree with it, regenerate those derived artifacts from `topology.json`.

A conforming `topology.json` is enough to reconstruct the labeled prefix roots without consulting derived artifacts or directory names. Source directory names such as `ripple` and `variant_<k>` are provenance labels, not schema fields.

Generated files must satisfy the closed schema below. Current CLI parsing may normalize or accept inputs that a strict validator would reject, so parser acceptance is not conformance. `make inspect` is a normalizer and summary tool, not a complete validator.

Generated JSON may be compact. Whitespace and object member order are not part of the contract.

Validator posture:

- validate object keys before reading field values
- reject extra descriptive fields instead of ignoring them
- derive suffix intervals from `dependency` and step index
- do not repair labels, dependencies, or shapes during conformance checking

## Conformance Snapshot

| Topic | Requirement |
| --- | --- |
| Document | one object with exactly `width` and `steps` |
| Width | positive integer $n$ |
| Step count | exactly $n - 1$ |
| Dependency | `steps[i].dependency` is an integer $d_i$ with $0 <= d_i <= i$ |
| Forced interval | step $i$ and dependency $d_i$ force $[d_i + 1, i + 1]$ |
| Suffix shape | ordered full binary tree |
| Suffix labels | explicit leaves covering the forced interval in ascending order |
| Width $1$ document | exactly `{"width":1,"steps":[]}` |

The key invariant is local to each step: once step index $i$ and dependency $d_i$ are known, every valid suffix label is forced. The suffix still stores those labels explicitly; the forced interval is the validator's check, not an omitted-data rule.

| Term | Meaning |
| --- | --- |
| root | a previously constructed prefix tree $R_k$ |
| dependency | the root index $d_i$ selected by step $i$ |
| forced interval | the suffix label range $[d_i + 1, i + 1]$ |
| suffix | the labeled ordered full tree appended to $R_{d_i}$ |

## JSON Shape

```text
topology-document:
  object with exactly:
    "width": integer
    "steps": array of step objects

step object:
  object with exactly:
    "dependency": integer
    "suffix": suffix-tree object

suffix-tree object:
  {"leaf": integer}
  or
  {"node": [suffix-tree object, suffix-tree object]}
```

Each suffix-tree object contains exactly one shape field: either `leaf` or `node`.

| Shape | Meaning |
| --- | --- |
| `{"leaf": k}` | terminal leaf labeled $k$ |
| `{"node": [L, R]}` | ordered binary node with left child `L` and right child `R` |

A leaf is terminal. A node has exactly two children and no label of its own. Integers are JSON numbers with no fractional component; strings, booleans, `null`, and fractional numbers are not conforming substitutes. `topology.json` must not store `shape`; Dyck words belong only in derived companion `dyck.json`.

Closed-schema consequences:

- a suffix object with both `leaf` and `node` is invalid even if one field appears usable
- a suffix object with neither `leaf` nor `node` is invalid
- a `leaf` label outside the forced interval is invalid even if the tree shape is otherwise full
- a node child order change is a topology change, not formatting

The unique valid width $1$ document is:

```json
{
  "width": 1,
  "steps": []
}
```

## Semantic Model

All indexes are zero-based. The initial constructed root is $R_0 = Leaf(0)$.

For each step index $i$, where $0 <= i <= n - 2$:

1. Read `dependency` as $d_i$.
2. Select the already constructed root $R_{d_i}$.
3. Derive the forced suffix interval $[d_i + 1, i + 1]$.
4. Interpret `suffix` as a labeled ordered full tree over exactly that interval.
5. Construct $R_{i + 1} = Node(R_{d_i}, S_i)$.

`dependency` points to a previously constructed root, not to another step's suffix. For `steps[0]`, the only valid dependency is $0$; later steps may select any root from $0$ through the current step index.

Every suffix leaf label is explicit data. Labels are not inferred from tree shape, source topology name, directory name, or `dependency`; they must match the forced interval exactly.

## Validation

A strict validator must reject a document when any rule below fails. Rule IDs are stable names for reviewers and tests; they are not fields stored in JSON.

Document and step rules:

| Rule | Requirement |
| --- | --- |
| `J1` | document must be a JSON object with exactly `width` and `steps` |
| `J2` | `width` must be an integer $n >= 1$ |
| `J3` | `steps` must be an array with exactly $n - 1$ elements |
| `J4` | each step must be an object with exactly `dependency` and `suffix` |
| `J5` | each `dependency` must be an integer |
| `T1` | each $d_i$ must satisfy $0 <= d_i <= i$ |
| `T2` | step index $i$ and $d_i$ determine interval $[d_i + 1, i + 1]$ |
| `T3` | `suffix` must be valid over the forced interval |

For a forced suffix interval $[a, b]$, rule `T3` expands to:

| Rule | Requirement |
| --- | --- |
| `S1` | if $a = b$, the tree must be exactly `{"leaf": a}` |
| `S2` | if $a < b$, the tree must be exactly `{"node": [L, R]}` |
| `S3` | the suffix-tree object must contain exactly the field required by `S1` or `S2` |
| `S4` | the `node` value must be an array of length exactly $2$ |
| `S5` | some split $k$ with $a <= k < b$ must make `L` valid over $[a, k]$ and `R` valid over $[k + 1, b]$ |

Together, these rules require every suffix to cover each leaf in the forced interval exactly once, in ascending order, with no descriptive or extra fields.

Reviewer checklist:

1. Confirm `width` is a positive integer.
2. Confirm `steps` has exactly $n - 1$ entries.
3. For each `steps[i]`, confirm `dependency` is an integer $d_i$ with $0 <= d_i <= i$.
4. Derive the forced suffix interval $[d_i + 1, i + 1]$.
5. Confirm `suffix` labels exactly that interval in ascending order.
6. Reject descriptive or extra fields anywhere in the document.

Use the [Dyck JSON Specification](dyck-json-spec.md) to check that a derived `dyck.json` companion has the same `width`, the same dependencies, and matching suffix shapes.

## Shared Example

The shared example is [`sample_topologies/width_4/variant_2/topology.json`](../sample_topologies/width_4/variant_2/topology.json). Its third step is the only step whose forced suffix interval contains more than one leaf.

```json
{
  "width": 4,
  "steps": [
    {"dependency": 0, "suffix": {"leaf": 1}},
    {"dependency": 1, "suffix": {"leaf": 2}},
    {
      "dependency": 0,
      "suffix": {
        "node": [
          {"leaf": 1},
          {"node": [{"leaf": 2}, {"leaf": 3}]}
        ]
      }
    }
  ]
}
```

| Step | Dependency | Forced interval | Why it conforms |
| --- | --- | --- | --- |
| `steps[0]` | $0$ | $[1, 1]$ | suffix is exactly `{"leaf": 1}` |
| `steps[1]` | $1$ | $[2, 2]$ | suffix is exactly `{"leaf": 2}` |
| `steps[2]` | $0$ | $[1, 3]$ | suffix splits as leaf $1$ and subtree over $[2, 3]$ |

At `steps[2]`, dependency value $0$ fixes interval $[1, 3]$ before `suffix` is interpreted. Leaf $2$ cannot be skipped, reordered, or inferred from another field.

The companion `dyck.json` carries the same `width` and `dependency` values, but replaces each labeled suffix with one shape-only `shape` string.

## Invalid Inputs

| Pattern | Reason | Violated rule |
| --- | --- | --- |
| `{"width":1,"steps":[],"extra":true}` | top-level object has an extra field | `J1` |
| `{"width":4.5,"steps":[]}` | `width` is not an integer | `J2` |
| `{"width":4,"steps":[]}` | `steps` must have length $n - 1$ | `J3` |
| step index $2$ with `dependency` value $3$ | dependency references a future root | `T1` |
| suffix `{"leaf":1,"node":[]}` | suffix object has two shape fields | `S3` |
| suffix `{"node":[{"leaf":1}]}` | node does not have exactly $2$ children | `S4` |
| step index $2$ with suffix leaves $1$, $3$ | missing leaf $2$ from $[1, 3]$ | `T3`, `S5` |
| step index $2$ with suffix leaves $2$, $1$, $3$ | leaves are not in ascending order | `T3`, `S5` |
| step object with `shape` instead of `suffix` | Dyck words belong only in `dyck.json` | `J4` |
| step object with an extra field | each step must contain exactly `dependency` and `suffix` | `J4` |

## Implementation Notes

Current behavior comes from [`../src/PrefixTopology.scala`](../src/PrefixTopology.scala) and [`../src/Main.scala`](../src/Main.scala). `make inspect` reads a topology file, re-emits canonical topology JSON, and prints derived cell counts.

Treat this document as the strict generated-artifact contract. Use a strict validator when closed-schema enforcement matters; use `make inspect` for canonicalization and quick cell-count inspection.
