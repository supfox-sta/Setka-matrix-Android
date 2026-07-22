package io.element.android.features.preferences.impl.user.changephone

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.bumble.appyx.core.modality.BuildContext
import com.bumble.appyx.core.node.Node
import com.bumble.appyx.core.plugin.Plugin
import dev.zacsweers.metro.Assisted
import dev.zacsweers.metro.AssistedInject
import io.element.android.annotations.ContributesNode
import io.element.android.libraries.architecture.callback
import io.element.android.libraries.di.SessionScope

@ContributesNode(SessionScope::class)
@AssistedInject
class ChangePhoneNumberNode(
    @Assisted buildContext: BuildContext,
    @Assisted plugins: List<Plugin>,
    presenterFactory: ChangePhoneNumberPresenter.Factory,
) : Node(buildContext, plugins = plugins),
    ChangePhoneNumberNavigator {
    interface Callback : Plugin {
        fun onDone(changed: Boolean)
    }

    private val callback: Callback = callback()
    private val presenter = presenterFactory.create(navigator = this)

    @Composable
    override fun View(modifier: Modifier) {
        val state = presenter.present()
        ChangePhoneNumberView(
            state = state,
            modifier = modifier,
        )
    }

    override fun close(changed: Boolean) {
        callback.onDone(changed)
    }
}
