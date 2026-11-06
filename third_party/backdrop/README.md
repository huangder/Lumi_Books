# Backdrop sampling patch

This module vendors the Kotlin source of `io.github.kyant0:backdrop:1.0.6`
from https://github.com/Kyant0/AndroidLiquidGlass (Copyright 2025 Kyant).
The upstream Apache 2.0 license is in LICENSE; local changes are described in NOTICE.
Source archive SHA-256: `a303f0f0b47a209fb5a3e86427cfbb1b93cb7a13c90a30116ef028d326b8a723`.

Local changes are limited to `BackdropSampling.kt`, `BackdropEffectScope.kt`,
`DrawBackdropModifier.kt`, `effects/Blur.kt` and `effects/Lens.kt`.
`LocalBackdropSamplingScale` defaults to 1, and the app supplies its selected
quality only within Liquid Glass themes. Effect buffers are recorded at the
selected pixel scale and enlarged back to logical screen coordinates. Blur
radii, rounded lens uniforms and the app's custom menu shader use the same scale.

Source backdrop layers remain command recordings rather than introducing an
extra full-page offscreen bitmap. Only sampled background effects change
resolution: shape clipping, surface tints, foreground text/icons, highlights,
shadows and exported backdrop coordinates keep their upstream behavior.
Window and native EPUB bitmap capture sizes are managed in the app.

To update upstream, compare these patched files with the new source archive,
retain the sampling hooks and recheck the frame/viewport alignment and live
quality switching on Android 12 (blur) and Android 13+ (lens).
