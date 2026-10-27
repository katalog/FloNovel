package android.net

import android.os.Parcel

fun testUri(name: String): Uri = object : Uri() {
    override fun toString(): String = "content://$name"
    override fun isHierarchical(): Boolean = true
    override fun isRelative(): Boolean = false
    override fun getScheme(): String = "content"
    override fun getSchemeSpecificPart(): String = "//$name"
    override fun getEncodedSchemeSpecificPart(): String = "//$name"
    override fun getAuthority(): String = name
    override fun getEncodedAuthority(): String = name
    override fun getUserInfo(): String? = null
    override fun getEncodedUserInfo(): String? = null
    override fun getHost(): String = name
    override fun getPort(): Int = -1
    override fun getPath(): String = ""
    override fun getEncodedPath(): String = ""
    override fun getQuery(): String? = null
    override fun getEncodedQuery(): String? = null
    override fun getFragment(): String? = null
    override fun getEncodedFragment(): String? = null
    override fun getPathSegments(): List<String> = emptyList()
    override fun getLastPathSegment(): String? = null
    override fun buildUpon(): Builder? = null
    override fun describeContents(): Int = 0
    override fun writeToParcel(dest: Parcel, flags: Int) {}
    override fun compareTo(other: Uri?): Int = 0
    override fun equals(other: Any?): Boolean = other is Uri && other.toString() == toString()
    override fun hashCode(): Int = toString().hashCode()
}
