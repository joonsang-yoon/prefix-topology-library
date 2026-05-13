# Dyck JSON Specification

`dyck.json` is the derived, shape-only companion to an authoritative `topology.json` file. This document is the strict generated-artifact contract for that companion format.

Use this spec when reviewing generated Dyck encodings or checking that a `dyck.json` file still matches its source topology. The authoritative labeled format is defined by the [Topology JSON Specification](topology-json-spec.md). Repository workflows and root layouts live in the [prefix-topology-library README](../README.md).

## Authority

`dyck.json` is never the topology source of truth. It preserves `width` and dependencies from `topology.json`, but replaces each labeled `suffix` tree with one shape-only `shape` string.

If `dyck.json` disagrees with its companion `topology.json`, regenerate it from `topology.json` instead of editing it as an alternate source.

The companion relationship is exact:

- `width` must match the paired `topology.json`
- every `dependency` must match the paired `topology.json`
- every `shape` must equal the encoded shape of the matching labeled suffix

Labels are recovered only from each forced interval $[d_i + 1, i + 1]$.

Generated files must satisfy the closed schema below. Current CLI behavior is centered on deriving `dyck.json` from `topology.json`; `make inspect` does not accept `dyck.json` as input or validate standalone Dyck files.

Generated JSON may be compact. Whitespace and object member order are not part of the contract.

Validator posture:

- validate the document shape and dependency bounds before decoding `shape`
- treat `""` as a real shape value for one-leaf suffixes
- reject extra fields instead of ignoring them
- use companion consistency, not standalone well formedness, when deciding whether a generated `dyck.json` is current

## Conformance Snapshot

| Topic | Requirement |
| --- | --- |
| Authority | derived companion only; `topology.json` remains authoritative |
| Document | one object with exactly `width` and `steps` |
| Width | positive integer $n$ |
| Step count | exactly $n - 1$ |
| Dependency | `steps[i].dependency` is an integer $d_i$ with $0 <= d_i <= i$ |
| Suffix size | $m = i + 1 - d_i$ |
| Shape | `shape` is a Dyck word for an ordered full tree with $m$ leaves |
| Labels | never stored; recovered left to right from $[d_i + 1, i + 1]$ |
| Width $1$ document | exactly `{"width":1,"steps":[]}` |

The empty string `""` is the required encoded value for a one-leaf suffix. It is a present, meaningful field value, not a missing shape.

The key difference from `topology.json` is that `dyck.json` omits labels by design. It can be well formed on its own, but it is authoritative for nothing until it is compared with the paired topology file.

| Term | Meaning |
| --- | --- |
| forced interval | the label range $[d_i + 1, i + 1]$ recovered from step index and dependency |
| suffix size | number of leaves $m = i + 1 - d_i$ |
| shape word | the `U`/`D` Dyck encoding stored in `shape` |
| companion check | comparison against the authoritative `topology.json` beside this file |

## JSON Shape

```text
dyck-document:
  object with exactly:
    "width": integer
    "steps": array of step objects

step object:
  object with exactly:
    "dependency": integer
    "shape": string containing only U and D
```

`dyck.json` must not store labeled `suffix` trees; labeled trees belong only in authoritative `topology.json`.

The unique valid width $1$ document is:

```json
{
  "width": 1,
  "steps": []
}
```

## Semantic Model

`dyck.json` uses the same root and dependency indexes as `topology.json`. At step index $i$:

1. Read `dependency` as $d_i$.
2. Derive the forced suffix interval $[d_i + 1, i + 1]$.
3. Compute the suffix size $m = i + 1 - d_i$.
4. Decode `shape` as only the ordered full-tree shape for size $m$.
5. Relabel decoded leaves left to right with $d_i + 1$ through $i + 1$.

`shape` never carries leaf labels. Step index and dependency fix the labels before the shape is decoded. The same `shape` string can represent different labeled suffixes at different steps.

## Shape Encoding

`shape` stores a label-free ordered full-tree shape.

| Tree | Encoded word |
| --- | --- |
| `Leaf(i)` | `""` |
| `Node(L, R)` | `"U" + encode(L) + "D" + encode(R)` |

Dyck decoding is the inverse:

- `decode("")` is a one-leaf tree.
- `decode("U" + x + "D" + y) = Node(decode(x), decode(y))`, where the `D` is the match for the first `U`.

For comparison, `"UDUD"` decodes as `Node(Leaf, Node(Leaf, Leaf))`, while `"UUDD"` decodes as `Node(Node(Leaf, Leaf), Leaf)`.

For a suffix with $m$ leaves, a valid encoded word has $m - 1$ `U` symbols and $m - 1$ `D` symbols. For $m = 1$, the only valid word is `""`.

Character set alone is not enough. Balance, length, forced suffix size, and full consumption of the string all matter.

Decoding notes:

- a valid word for $m$ leaves has exactly $2(m - 1)$ characters
- the first unmatched `D` makes the word invalid immediately
- a word that has valid balance but the wrong size is still invalid for that step
- decoded leaves are labels only after relabeling from the forced interval

## Validation

A `dyck.json` file can be reviewed at two levels.

| Review level | What it proves |
| --- | --- |
| Standalone well formed | fields, dependencies, and Dyck words are valid for this file's own width |
| Companion consistent | the file also matches the paired `topology.json` width, dependencies, and suffix shapes |

Standalone validation proves only that the shape encoding is valid for the document's own dependencies. Companion consistency proves that `dyck.json` is the derived view of a specific `topology.json`, so it is the stronger check whenever both files are available.

Rule IDs are stable names for reviewers and tests; they are not fields stored in JSON.

Document and dependency rules:

| Rule | Requirement |
| --- | --- |
| `J1` | document must be a JSON object with exactly `width` and `steps` |
| `J2` | `width` must be an integer $n >= 1$ |
| `J3` | `steps` must be an array with exactly $n - 1$ elements |
| `J4` | each step must be an object with exactly `dependency` and `shape` |
| `J5` | each `dependency` must be an integer |
| `C1` | each $d_i$ must satisfy $0 <= d_i <= i$ |
| `C2` | step index $i$ and $d_i$ determine forced interval $[d_i + 1, i + 1]$ and suffix size $m$ |

Companion consistency rules:

| Rule | Requirement |
| --- | --- |
| `C3` | when compared with `topology.json`, `width` and every `dependency` value must match |
| `C4` | when compared with `topology.json`, every `shape` must equal the encoded shape of the matching `suffix` |

Shape-word rules:

| Rule | Requirement |
| --- | --- |
| `W1` | `shape` must be a string containing only `U` and `D` |
| `W2` | for suffix size $m$, the string length must be exactly $2(m - 1)$ |
| `W3` | every prefix must contain at least as many `U` symbols as `D` symbols |
| `W4` | the full string must contain exactly $m - 1$ `U` symbols and $m - 1$ `D` symbols |

Together, these rules require `shape` to encode only a valid ordered full-tree shape for the forced suffix size.

Reviewer checklist:

1. Confirm `width` is a positive integer.
2. Confirm `steps` has exactly $n - 1$ entries.
3. For each `steps[i]`, confirm `dependency` is an integer $d_i$ with $0 <= d_i <= i$.
4. Compute the forced suffix size $m = i + 1 - d_i$.
5. Confirm `shape` is a valid Dyck word for exactly $m$ leaves.
6. Compare `width`, every `dependency`, and every suffix shape with the authoritative `topology.json` companion.
7. Regenerate `dyck.json` from `topology.json` when they disagree.

Do not validate `shape` only by character set.

When a standalone check passes but a companion check fails, keep `topology.json` and regenerate `dyck.json`.

## Shared Example

The shared example is [`sample_topologies/width_4/variant_2/dyck.json`](../sample_topologies/width_4/variant_2/dyck.json), derived from [`sample_topologies/width_4/variant_2/topology.json`](../sample_topologies/width_4/variant_2/topology.json). Its third step is the only step whose forced suffix interval contains more than one leaf, so it is the only non-empty Dyck word.

```json
{
  "width": 4,
  "steps": [
    {"dependency": 0, "shape": ""},
    {"dependency": 1, "shape": ""},
    {"dependency": 0, "shape": "UDUD"}
  ]
}
```

| Step | Dependency | Forced interval and size | Stored word |
| --- | --- | --- | --- |
| `steps[0]` | $0$ | $[1, 1]$, $m = 1$ | `""` |
| `steps[1]` | $1$ | $[2, 2]$, $m = 1$ | `""` |
| `steps[2]` | $0$ | $[1, 3]$, $m = 3$ | `"UDUD"` |

At `steps[2]`, `shape = "UDUD"` determines only `Node(Leaf, Node(Leaf, Leaf))`. Relabeling from the forced suffix interval yields `Node(Leaf(1), Node(Leaf(2), Leaf(3)))`.

## Invalid Inputs

| Pattern | Reason | Violated rule |
| --- | --- | --- |
| `{"width":1,"steps":[],"extra":true}` | top-level object has an extra field | `J1` |
| width $4$ with only $2$ steps | `steps` must have length $n - 1$ | `J3` |
| step index $2$ with `dependency` value $3$ | dependency references a future root | `C1` |
| `shape = ["U","D"]` | `shape` must be a string | `W1` |
| `shape = "UXUD"` | only `U` and `D` are valid symbols | `W1` |
| step index $0$ with `shape = "UD"` | for $m = 1$, the unique valid word is `""` | `W2` |
| suffix size $2$ with `shape = "UDUD"` | length must be exactly $2(m - 1) = 2$ | `W2` |
| suffix size $3$ with `shape = "UUD"` | length must be exactly $2(m - 1) = 4$ | `W2` |
| suffix size $3$ with `shape = "DUUD"` | the first symbol closes before any open `U` | `W3` |
| suffix size $3$ with `shape = "UUUU"` | the word never balances | `W4` |
| companion `topology.json` has a different dependency | the derived file no longer matches its source | `C3` |
| companion `topology.json` has a different suffix shape | the derived file no longer matches its source | `C4` |
| step object with labeled `suffix` instead of `shape` | labeled trees belong only in `topology.json` | `J4` |
| step object with an extra field | each step must contain exactly `dependency` and `shape` | `J4` |

## Comparison With `topology.json`

| Data | `topology.json` | `dyck.json` |
| --- | --- | --- |
| Width | `width` | same `width` |
| Dependencies | `steps[i].dependency` | same `steps[i].dependency` |
| Suffix labels | explicit `leaf` values | omitted, recovered from $[d_i + 1, i + 1]$ |
| Suffix shape | nested `leaf` and `node` objects | encoded as `shape` |
| Authority | source of truth | derived companion |

Because labels are omitted, a standalone `dyck.json` can prove only that its own shape words are well formed for its own dependencies. It does not prove that the file matches a particular topology unless it is compared with that topology's `topology.json`.

## Implementation Notes

Current behavior comes from [`../src/PrefixTopology.scala`](../src/PrefixTopology.scala), [`../src/TopologyArtifacts.scala`](../src/TopologyArtifacts.scala), and [`../src/Main.scala`](../src/Main.scala). `dyck.json` is derived output rather than an accepted topology input.

Treat this document as the strict generated-artifact contract, and use authoritative `topology.json` as the source to regenerate derived Dyck files.
