package com.github.xmlet.htmlflow.testviews.kdoc

import org.http4k.template.ViewModel

/** Test ViewModels that match the KDoc examples in findCompatibleView */

// Example 1: Direct match
data class UserVm(val name: String) : ViewModel

// Example 2 & 3: Inheritance chain
open class BaseVm(open val baseContent: String) : ViewModel

class DerivedVm(override val baseContent: String, val derivedContent: String) : BaseVm(baseContent)

// Example 4: Interface match
interface ProfileLike : ViewModel {
    val name: String
}

data class PublicProfile(override val name: String, val bio: String) : ProfileLike

// Example 5: Assignable fallback (no inheritance/interface chain to registered type)
data class UnrelatedVm(val data: String) : ViewModel { // no inheritance or interface
    override fun toString(): String = data
}

interface SecondaryInterface : ViewModel {
    val secondary: String
}

class MultiInterfaceVm(override val name: String, override val secondary: String) :
    ProfileLike, SecondaryInterface
