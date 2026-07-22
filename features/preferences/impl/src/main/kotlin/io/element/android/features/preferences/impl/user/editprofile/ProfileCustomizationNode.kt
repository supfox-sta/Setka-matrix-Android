package io.element.android.features.preferences.impl.user.editprofile

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.bumble.appyx.core.modality.BuildContext
import com.bumble.appyx.core.node.Node
import com.bumble.appyx.core.plugin.Plugin
import dev.zacsweers.metro.Assisted
import dev.zacsweers.metro.AssistedInject
import io.element.android.annotations.ContributesNode
import io.element.android.libraries.architecture.NodeInputs
import io.element.android.libraries.architecture.callback
import io.element.android.libraries.architecture.inputs
import io.element.android.libraries.di.SessionScope
import io.element.android.libraries.matrix.api.user.MatrixUser

@ContributesNode(SessionScope::class)
@AssistedInject
class ProfileCustomizationNode(
    @Assisted buildContext: BuildContext,
    @Assisted plugins: List<Plugin>,
    presenterFactory: ProfileCustomizationPresenter.Factory,
) : Node(buildContext, plugins = plugins),
    ProfileCustomizationNavigator {
    data class Inputs(
        val matrixUser: MatrixUser,
    ) : NodeInputs

    interface Callback : Plugin {
        fun onDone()
    }

    val matrixUser = inputs<Inputs>().matrixUser
    private val callback: Callback = callback()
    private val presenter = presenterFactory.create(
        matrixUser = matrixUser,
        navigator = this,
    )

    @Composable
    override fun View(modifier: Modifier) {
        val state = presenter.present()
        ProfileCustomizationView(
            state = state,
            onSaveSuccess = ::close,
            modifier = modifier,
        )
    }

    override fun close() = callback.onDone()
}
