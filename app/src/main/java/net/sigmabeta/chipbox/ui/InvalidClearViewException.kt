package net.sigmabeta.chipbox.ui

class InvalidClearViewException(view: BaseView?) : RuntimeException() {

    val errorMessage: String

    init {
        this.errorMessage = "Cannot clear reference to  ${view?.javaClass?.simpleName}" +
                ": Presenter already has another reference."
    }

    override val message: String?
        get() = errorMessage
}

