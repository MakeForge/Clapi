# Contract: Variable Data Model & Polymorphic Serialization

**Branch**: `028-typed-dynamic-variables` | **Date**: 2026-11-16 | **Spec**: [spec.md](../spec.md)

---

## 1. VariableType Specification

```kotlin
@Serializable
enum class VariableType(val value: String) {
    @SerialName("string")
    STRING("string"),
    @SerialName("number")
    NUMBER("number"),
    @SerialName("boolean")
    BOOLEAN("boolean"),
    @SerialName("null")
    NULL("null"),
    @SerialName("object")
    OBJECT("object");

    override fun toString(): String = value

    companion object {
        fun fromValue(value: String?): VariableType? =
            entries.firstOrNull { it.value.equals(value, ignoreCase = true) }
    }
}
```

---

## 2. Polymorphic Deserialization Rules (`VariableEntrySerializer`)

Given a JSON object representing a `VariableEntry`:
1. If `element.jsonObject["secret"]?.jsonPrimitive?.booleanOrNull == true`:
   - Deserialize as `SecretVariable`.
2. Else if `element.jsonObject.containsKey("script")` OR `element.jsonObject["type"]?.jsonPrimitive?.contentOrNull == "dynamic"`:
   - Deserialize as `DynamicVariable`.
   - If `type` was `"dynamic"`, map `DynamicVariable.type` to `VariableType.STRING` by default.
3. Otherwise:
   - Deserialize as `RegularVariable`.

---

## 3. Dynamic Variable Validation Rules at Runtime

When executing a dynamic variable script via `UserScriptDynamicVariableProvider`:
- If `dynamicVar.type == VariableType.NUMBER`:
  - Returned string MUST parse via `toDoubleOrNull() != null`.
  - Violations throw `DynamicVariableResolutionException`.
- If `dynamicVar.type == VariableType.BOOLEAN`:
  - Returned string MUST equal `"true"` or `"false"`.
  - Violations throw `DynamicVariableResolutionException`.
- If `dynamicVar.type == VariableType.OBJECT`:
  - Returned string MUST start with `{` and end with `}` OR start with `[` and end with `]`.
  - Violations throw `DynamicVariableResolutionException`.
- If `dynamicVar.type == VariableType.STRING`:
  - Any return value is accepted.
