package android.content
class Context {
    val applicationContext get() = this
    private val values = mutableMapOf<String, Any>()
    fun getSharedPreferences(name: String, mode: Int) = Preferences(values)
    companion object { const val MODE_PRIVATE = 0 }
}
class Preferences(private val values: MutableMap<String, Any>) {
    fun getString(key:String, def:String?):String? = values[key] as? String ?: def
    @Suppress("UNCHECKED_CAST") fun getStringSet(key:String, def:Set<String>?):Set<String>? = (values[key] as? Set<String>)?.toSet() ?: def
    fun edit() = Editor(values)
    class Editor(private val values:MutableMap<String,Any>) {
        fun putString(k:String,v:String) = apply { values[k]=v }
        fun putStringSet(k:String,v:Set<String>) = apply { values[k]=v.toSet() }
        fun apply() = Unit
    }
}
