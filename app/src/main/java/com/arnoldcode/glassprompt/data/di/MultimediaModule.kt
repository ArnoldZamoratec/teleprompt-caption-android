package com.arnoldcode.glassprompt.data.di

import com.arnoldcode.glassprompt.core.camera.CameraController
import com.arnoldcode.glassprompt.data.multimedia.camera.CameraXController
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.android.components.ViewModelComponent

/** Camera and media engines. Unscoped: each screen's ViewModel owns its own instance. */
@Module
@InstallIn(ViewModelComponent::class)
abstract class MultimediaModule {

    @Binds
    abstract fun bindCameraController(impl: CameraXController): CameraController
}
