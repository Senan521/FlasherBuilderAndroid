package app.flasherbuilder.data

enum class Phase { Idle, Running, Done, Failed }

data class Ui(
    val phase: Phase = Phase.Idle,
    val message: String = "",
    val progress: Float? = null,
    val resultPath: String? = null,
) {
    val busy get() = phase == Phase.Running
}
