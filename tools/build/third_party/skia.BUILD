# Copyright (C) 2019 Google Inc.
#
# Licensed under the Apache License, Version 2.0 (the "License");
# you may not use this file except in compliance with the License.
# You may obtain a copy of the License at
#
#      http://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing, software
# distributed under the License is distributed on an "AS IS" BASIS,
# WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
# See the License for the specific language governing permissions and
# limitations under the License.

load("@gapid//tools/build/rules:cc.bzl", "cc_copts")

_CORE_SRCS = [
    "src/c/sk_imageinfo.cpp",
    "src/c/sk_paint.cpp",
    "src/c/sk_surface.cpp",
    "src/core/SkAAClip.cpp",
    "src/core/SkAnnotation.cpp",
    "src/core/SkAlphaRuns.cpp",
    "src/core/SkATrace.cpp",
    "src/core/SkAutoPixmapStorage.cpp",
    "src/core/SkBBHFactory.cpp",
    "src/core/SkBitmap.cpp",
    "src/core/SkBitmapCache.cpp",
    "src/core/SkBitmapController.cpp",
    "src/core/SkBitmapDevice.cpp",
    "src/core/SkBitmapProcState.cpp",
    "src/core/SkBitmapProcState_matrixProcs.cpp",
    "src/core/SkBitmapProvider.cpp",
    "src/core/SkBlendMode.cpp",
    "src/core/SkBlitRow_D32.cpp",
    "src/core/SkBlitter.cpp",
    "src/core/SkBlitter_A8.cpp",
    "src/core/SkBlitter_ARGB32.cpp",
    "src/core/SkBlitter_RGB565.cpp",
    "src/core/SkBlitter_Sprite.cpp",
    "src/core/SkBlurMask.cpp",
    "src/core/SkBlurMF.cpp",
    "src/core/SkBuffer.cpp",
    "src/core/SkCachedData.cpp",
    "src/core/SkCanvas.cpp",
    "src/core/SkCanvasPriv.cpp",
    "src/core/SkCoverageDelta.cpp",
    "src/core/SkClipStack.cpp",
    "src/core/SkClipStackDevice.cpp",
    "src/core/SkColor.cpp",
    "src/core/SkColorFilter.cpp",
    "src/core/SkColorMatrixFilterRowMajor255.cpp",
    "src/core/SkColorSpace.cpp",
    "src/core/SkColorSpaceXformCanvas.cpp",
    "src/core/SkColorSpaceXformSteps.cpp",
    "src/core/SkColorSpaceXformer.cpp",
    "src/core/SkConvertPixels.cpp",
    "src/core/SkCpu.cpp",
    "src/core/SkCubicClipper.cpp",
    "src/core/SkCubicMap.cpp",
    "src/core/SkData.cpp",
    "src/core/SkDataTable.cpp",
    "src/core/SkDebug.cpp",
    "src/core/SkDeferredDisplayList.cpp",
    "src/core/SkDeferredDisplayListRecorder.cpp",
    "src/core/SkDeque.cpp",
    "src/core/SkDescriptor.cpp",
    "src/core/SkDevice.cpp",
    "src/lazy/SkDiscardableMemoryPool.cpp",
    "src/core/SkDistanceFieldGen.cpp",
    "src/core/SkDocument.cpp",
    "src/core/SkDraw.cpp",
    "src/core/SkDraw_text.cpp",
    "src/core/SkDraw_vertices.cpp",
    "src/core/SkDrawable.cpp",
    "src/core/SkDrawLooper.cpp",
    "src/core/SkDrawShadowInfo.cpp",
    "src/core/SkEdgeBuilder.cpp",
    "src/core/SkEdgeClipper.cpp",
    "src/core/SkExecutor.cpp",
    "src/core/SkAnalyticEdge.cpp",
    "src/core/SkEdge.cpp",
    "src/core/SkArenaAlloc.cpp",
    "src/core/SkGaussFilter.cpp",
    "src/core/SkFlattenable.cpp",
    "src/core/SkFont.cpp",
    "src/core/SkFontLCDConfig.cpp",
    "src/core/SkFontMgr.cpp",
    "src/core/SkFontDescriptor.cpp",
    "src/core/SkFontStream.cpp",
    "src/core/SkGeometry.cpp",
    "src/core/SkGlobalInitialization_core.cpp",
    "src/core/SkGlyph.cpp",
    "src/core/SkStrike.cpp",
    "src/core/SkGlyphRun.cpp",
    "src/core/SkGlyphRunPainter.cpp",
    "src/core/SkGpuBlurUtils.cpp",
    "src/core/SkGraphics.cpp",
    "src/core/SkHalf.cpp",
    "src/core/SkICC.cpp",
    "src/core/SkImageFilter.cpp",
    "src/core/SkImageFilterCache.cpp",
    "src/core/SkImageInfo.cpp",
    "src/core/SkImageGenerator.cpp",
    "src/core/SkLights.cpp",
    "src/core/SkLineClipper.cpp",
    "src/core/SkLiteDL.cpp",
    "src/core/SkLiteRecorder.cpp",
    "src/core/SkLocalMatrixImageFilter.cpp",
    "src/core/SkMD5.cpp",
    "src/core/SkMallocPixelRef.cpp",
    "src/core/SkMask.cpp",
    "src/core/SkMaskBlurFilter.cpp",
    "src/core/SkMaskCache.cpp",
    "src/core/SkMaskFilter.cpp",
    "src/core/SkMaskGamma.cpp",
    "src/core/SkMath.cpp",
    "src/core/SkMatrix.cpp",
    "src/core/SkMatrix44.cpp",
    "src/core/SkMatrixImageFilter.cpp",
    "src/core/SkMetaData.cpp",
    "src/core/SkMipMap.cpp",
    "src/core/SkMiniRecorder.cpp",
    "src/core/SkModeColorFilter.cpp",
    "src/core/SkLatticeIter.cpp",
    "src/core/SkNormalFlatSource.cpp",
    "src/core/SkNormalMapSource.cpp",
    "src/core/SkNormalSource.cpp",
    "src/core/SkOpts.cpp",
    "src/core/SkOverdrawCanvas.cpp",
    "src/core/SkPaint.cpp",
    "src/core/SkPaint_text.cpp",
    "src/core/SkPaintPriv.cpp",
    "src/core/SkPath.cpp",
    "src/core/SkPath_serial.cpp",
    "src/core/SkPathEffect.cpp",
    "src/core/SkPathMeasure.cpp",
    "src/core/SkPathRef.cpp",
    "src/core/SkPixelRef.cpp",
    "src/core/SkPixmap.cpp",
    "src/core/SkPoint.cpp",
    "src/core/SkPoint3.cpp",
    "src/core/SkPromiseImageTexture.cpp",
    "src/core/SkPtrRecorder.cpp",
    "src/core/SkQuadClipper.cpp",
    "src/core/SkRasterClip.cpp",
    "src/core/SkRasterPipeline.cpp",
    "src/core/SkRasterPipelineBlitter.cpp",
    "src/core/SkReadBuffer.cpp",
    "src/core/SkRecord.cpp",
    "src/core/SkRecords.cpp",
    "src/core/SkRecordDraw.cpp",
    "src/core/SkRecordOpts.cpp",
    "src/core/SkRect.cpp",
    "src/core/SkRegion.cpp",
    "src/core/SkRegion_path.cpp",
    "src/core/SkRemoteGlyphCache.cpp",
    "src/core/SkResourceCache.cpp",
    "src/core/SkRRect.cpp",
    "src/core/SkRTree.cpp",
    "src/core/SkRWBuffer.cpp",
    "src/core/SkScalar.cpp",
    "src/core/SkScalerContext.cpp",
    "src/core/SkScan.cpp",
    "src/core/SkScan_AAAPath.cpp",
    "src/core/SkScan_DAAPath.cpp",
    "src/core/SkScan_AntiPath.cpp",
    "src/core/SkScan_Antihair.cpp",
    "src/core/SkScan_Hairline.cpp",
    "src/core/SkScan_Path.cpp",
    "src/core/SkSemaphore.cpp",
    "src/core/SkSharedMutex.cpp",
    "src/core/SkSpecialImage.cpp",
    "src/core/SkSpecialSurface.cpp",
    "src/core/SkSpinlock.cpp",
    "src/core/SkSpriteBlitter_ARGB32.cpp",
    "src/core/SkSpriteBlitter_RGB565.cpp",
    "src/core/SkStream.cpp",
    "src/core/SkStrikeCache.cpp",
    "src/core/SkString.cpp",
    "src/core/SkStringUtils.cpp",
    "src/core/SkStroke.cpp",
    "src/core/SkStrokeRec.cpp",
    "src/core/SkStrokerPriv.cpp",
    "src/core/SkSurfaceCharacterization.cpp",
    "src/core/SkSwizzle.cpp",
    "src/core/SkTaskGroup.cpp",
    "src/core/SkTextBlob.cpp",
    "src/core/SkTime.cpp",
    "src/core/SkThreadID.cpp",
    "src/core/SkTLS.cpp",
    "src/core/SkTSearch.cpp",
    "src/core/SkTypeface.cpp",
    "src/core/SkTypeface_remote.cpp",
    "src/core/SkTypefaceCache.cpp",
    "src/core/SkUnPreMultiply.cpp",
    "src/core/SkUtils.cpp",
    "src/core/SkVertices.cpp",
    "src/core/SkVertState.cpp",
    "src/core/SkWriteBuffer.cpp",
    "src/core/SkWriter32.cpp",
    "src/core/SkXfermode.cpp",
    "src/core/SkXfermodeInterpretation.cpp",
    "src/core/SkYUVPlanesCache.cpp",
    "src/core/SkYUVASizeInfo.cpp",
    "src/image/SkImage.cpp",
    "src/image/SkImage_Lazy.cpp",
    "src/image/SkImage_Raster.cpp",
    "src/image/SkSurface.cpp",
    "src/image/SkSurface_Raster.cpp",
    "src/shaders/SkBitmapProcShader.cpp",
    "src/shaders/SkColorFilterShader.cpp",
    "src/shaders/SkColorShader.cpp",
    "src/shaders/SkComposeShader.cpp",
    "src/shaders/SkImageShader.cpp",
    "src/shaders/SkLightingShader.cpp",
    "src/shaders/SkLocalMatrixShader.cpp",
    "src/shaders/SkShader.cpp",
    "src/pathops/SkAddIntersections.cpp",
    "src/pathops/SkDConicLineIntersection.cpp",
    "src/pathops/SkDCubicLineIntersection.cpp",
    "src/pathops/SkDCubicToQuads.cpp",
    "src/pathops/SkDLineIntersection.cpp",
    "src/pathops/SkDQuadLineIntersection.cpp",
    "src/pathops/SkIntersections.cpp",
    "src/pathops/SkOpAngle.cpp",
    "src/pathops/SkOpBuilder.cpp",
    "src/pathops/SkOpCoincidence.cpp",
    "src/pathops/SkOpContour.cpp",
    "src/pathops/SkOpCubicHull.cpp",
    "src/pathops/SkOpEdgeBuilder.cpp",
    "src/pathops/SkOpSegment.cpp",
    "src/pathops/SkOpSpan.cpp",
    "src/pathops/SkPathOpsAsWinding.cpp",
    "src/pathops/SkPathOpsCommon.cpp",
    "src/pathops/SkPathOpsConic.cpp",
    "src/pathops/SkPathOpsCubic.cpp",
    "src/pathops/SkPathOpsCurve.cpp",
    "src/pathops/SkPathOpsDebug.cpp",
    "src/pathops/SkPathOpsLine.cpp",
    "src/pathops/SkPathOpsOp.cpp",
    "src/pathops/SkPathOpsQuad.cpp",
    "src/pathops/SkPathOpsRect.cpp",
    "src/pathops/SkPathOpsSimplify.cpp",
    "src/pathops/SkPathOpsTSect.cpp",
    "src/pathops/SkPathOpsTightBounds.cpp",
    "src/pathops/SkPathOpsTypes.cpp",
    "src/pathops/SkPathOpsWinding.cpp",
    "src/pathops/SkPathWriter.cpp",
    "src/pathops/SkReduceOrder.cpp",
    "src/core/SkBigPicture.cpp",
    "src/core/SkMultiPictureDraw.cpp",
    "src/core/SkPicture.cpp",
    "src/core/SkPictureData.cpp",
    "src/core/SkPictureFlat.cpp",
    "src/core/SkPictureImageGenerator.cpp",
    "src/core/SkPicturePlayback.cpp",
    "src/core/SkPictureRecord.cpp",
    "src/core/SkPictureRecorder.cpp",
    "src/core/SkRecordedDrawable.cpp",
    "src/core/SkRecorder.cpp",
    "src/shaders/SkPictureShader.cpp",
]

_EFFECTS_SRCS = [
    "src/c/sk_effects.cpp",
    "src/effects/Sk1DPathEffect.cpp",
    "src/effects/Sk2DPathEffect.cpp",
    "src/effects/SkColorMatrix.cpp",
    "src/effects/SkColorMatrixFilter.cpp",
    "src/effects/SkCornerPathEffect.cpp",
    "src/effects/SkDashPathEffect.cpp",
    "src/effects/SkDiscretePathEffect.cpp",
    "src/effects/SkEmbossMask.cpp",
    "src/effects/SkEmbossMaskFilter.cpp",
    "src/effects/SkHighContrastFilter.cpp",
    "src/effects/SkLayerDrawLooper.cpp",
    "src/effects/SkLumaColorFilter.cpp",
    "src/effects/SkOpPathEffect.cpp",
    "src/effects/SkOverdrawColorFilter.cpp",
    "src/effects/SkPackBits.cpp",
    "src/effects/SkShaderMaskFilter.cpp",
    "src/effects/SkTableColorFilter.cpp",
    "src/effects/SkTableMaskFilter.cpp",
    "src/effects/SkToSRGBColorFilter.cpp",
    "src/effects/SkTrimPathEffect.cpp",
    "src/shaders/SkPerlinNoiseShader.cpp",
    "src/shaders/gradients/Sk4fGradientBase.cpp",
    "src/shaders/gradients/Sk4fLinearGradient.cpp",
    "src/shaders/gradients/SkGradientShader.cpp",
    "src/shaders/gradients/SkLinearGradient.cpp",
    "src/shaders/gradients/SkRadialGradient.cpp",
    "src/shaders/gradients/SkTwoPointConicalGradient.cpp",
    "src/shaders/gradients/SkSweepGradient.cpp",
]

_GPU_SRCS = [
    "src/gpu/GrAuditTrail.cpp",
    "src/gpu/GrBackendSurface.cpp",
    "src/gpu/GrBackendTextureImageGenerator.cpp",
    "src/gpu/GrAHardwareBufferImageGenerator.cpp",
    "src/gpu/GrBitmapTextureMaker.cpp",
    "src/gpu/GrBlurUtils.cpp",
    "src/gpu/GrBuffer.cpp",
    "src/gpu/GrBufferAllocPool.cpp",
    "src/gpu/GrCaps.cpp",
    "src/gpu/GrClipStackClip.cpp",
    "src/gpu/GrColorSpaceInfo.cpp",
    "src/gpu/GrColorSpaceXform.cpp",
    "src/gpu/GrContext.cpp",
    "src/gpu/GrDDLContext.cpp",
    "src/gpu/GrDefaultGeoProcFactory.cpp",
    "src/gpu/GrDeinstantiateProxyTracker.cpp",
    "src/gpu/GrDirectContext.cpp",
    "src/gpu/GrDistanceFieldGenFromVector.cpp",
    "src/gpu/GrDrawingManager.cpp",
    "src/gpu/GrDrawOpAtlas.cpp",
    "src/gpu/GrDrawOpTest.cpp",
    "src/gpu/GrDriverBugWorkarounds.cpp",
    "src/gpu/GrFixedClip.cpp",
    "src/gpu/GrFragmentProcessor.cpp",
    "src/gpu/GrGpu.cpp",
    "src/gpu/GrGpuCommandBuffer.cpp",
    "src/gpu/GrGpuResource.cpp",
    "src/gpu/GrImageTextureMaker.cpp",
    "src/gpu/GrMemoryPool.cpp",
    "src/gpu/GrOpFlushState.cpp",
    "src/gpu/GrOpList.cpp",
    "src/gpu/GrPaint.cpp",
    "src/gpu/GrPathRendererChain.cpp",
    "src/gpu/GrPathRenderer.cpp",
    "src/gpu/GrPathUtils.cpp",
    "src/gpu/GrOnFlushResourceProvider.cpp",
    "src/gpu/GrPipeline.cpp",
    "src/gpu/GrPrimitiveProcessor.cpp",
    "src/gpu/GrProcessorSet.cpp",
    "src/gpu/GrProgramDesc.cpp",
    "src/gpu/GrProcessor.cpp",
    "src/gpu/GrProcessorAnalysis.cpp",
    "src/gpu/GrProcessorUnitTest.cpp",
    "src/gpu/GrProxyProvider.cpp",
    "src/gpu/GrQuad.cpp",
    "src/gpu/GrRectanizer_pow2.cpp",
    "src/gpu/GrRectanizer_skyline.cpp",
    "src/gpu/GrRenderTarget.cpp",
    "src/gpu/GrRenderTargetProxy.cpp",
    "src/gpu/GrReducedClip.cpp",
    "src/gpu/GrRenderTargetContext.cpp",
    "src/gpu/GrRenderTargetOpList.cpp",
    "src/gpu/GrResourceAllocator.cpp",
    "src/gpu/GrResourceCache.cpp",
    "src/gpu/GrResourceProvider.cpp",
    "src/gpu/GrShaderCaps.cpp",
    "src/gpu/GrShape.cpp",
    "src/gpu/GrStencilAttachment.cpp",
    "src/gpu/GrStencilSettings.cpp",
    "src/gpu/GrStyle.cpp",
    "src/gpu/GrTessellator.cpp",
    "src/gpu/GrTextureOpList.cpp",
    "src/gpu/GrTestUtils.cpp",
    "src/gpu/GrShaderVar.cpp",
    "src/gpu/GrSKSLPrettyPrint.cpp",
    "src/gpu/GrSoftwarePathRenderer.cpp",
    "src/gpu/GrSurface.cpp",
    "src/gpu/GrSurfaceContext.cpp",
    "src/gpu/GrSurfaceProxy.cpp",
    "src/gpu/GrSWMaskHelper.cpp",
    "src/gpu/GrTexture.cpp",
    "src/gpu/GrTextureAdjuster.cpp",
    "src/gpu/GrTextureContext.cpp",
    "src/gpu/GrTextureMaker.cpp",
    "src/gpu/GrTextureProducer.cpp",
    "src/gpu/GrTextureProxy.cpp",
    "src/gpu/GrTextureRenderTargetProxy.cpp",
    "src/gpu/GrXferProcessor.cpp",
    "src/gpu/GrYUVProvider.cpp",
    "src/gpu/ops/GrAAConvexTessellator.cpp",
    "src/gpu/ops/GrAAConvexPathRenderer.cpp",
    "src/gpu/ops/GrAAFillRRectOp.cpp",
    "src/gpu/ops/GrAAHairLinePathRenderer.cpp",
    "src/gpu/ops/GrAALinearizingConvexPathRenderer.cpp",
    "src/gpu/ops/GrAtlasTextOp.cpp",
    "src/gpu/ops/GrClearOp.cpp",
    "src/gpu/ops/GrClearStencilClipOp.cpp",
    "src/gpu/ops/GrCopySurfaceOp.cpp",
    "src/gpu/ops/GrDashLinePathRenderer.cpp",
    "src/gpu/ops/GrDashOp.cpp",
    "src/gpu/ops/GrDefaultPathRenderer.cpp",
    "src/gpu/ops/GrDebugMarkerOp.cpp",
    "src/gpu/ops/GrDrawableOp.cpp",
    "src/gpu/ops/GrDrawAtlasOp.cpp",
    "src/gpu/ops/GrDrawVerticesOp.cpp",
    "src/gpu/ops/GrFillRectOp.cpp",
    "src/gpu/ops/GrMeshDrawOp.cpp",
    "src/gpu/ops/GrLatticeOp.cpp",
    "src/gpu/ops/GrOp.cpp",
    "src/gpu/ops/GrOvalOpFactory.cpp",
    "src/gpu/ops/GrQuadPerEdgeAA.cpp",
    "src/gpu/ops/GrRegionOp.cpp",
    "src/gpu/ops/GrSemaphoreOp.cpp",
    "src/gpu/ops/GrShadowRRectOp.cpp",
    "src/gpu/ops/GrSimpleMeshDrawOpHelper.cpp",
    "src/gpu/ops/GrSmallPathRenderer.cpp",
    "src/gpu/ops/GrStrokeRectOp.cpp",
    "src/gpu/ops/GrTessellatingPathRenderer.cpp",
    "src/gpu/ops/GrTextureOp.cpp",
    "src/gpu/effects/GrAARectEffect.cpp",
    "src/gpu/effects/GrAlphaThresholdFragmentProcessor.cpp",
    "src/gpu/effects/GrBlurredEdgeFragmentProcessor.cpp",
    "src/gpu/effects/GrCircleBlurFragmentProcessor.cpp",
    "src/gpu/effects/GrCircleEffect.cpp",
    "src/gpu/effects/GrConfigConversionEffect.cpp",
    "src/gpu/effects/GrConstColorProcessor.cpp",
    "src/gpu/effects/GrCoverageSetOpXP.cpp",
    "src/gpu/effects/GrCustomXfermode.cpp",
    "src/gpu/effects/GrBezierEffect.cpp",
    "src/gpu/effects/GrConvexPolyEffect.cpp",
    "src/gpu/effects/GrBicubicEffect.cpp",
    "src/gpu/effects/GrBitmapTextGeoProc.cpp",
    "src/gpu/effects/GrDisableColorXP.cpp",
    "src/gpu/effects/GrDistanceFieldGeoProc.cpp",
    "src/gpu/effects/GrEllipseEffect.cpp",
    "src/gpu/effects/GrGaussianConvolutionFragmentProcessor.cpp",
    "src/gpu/effects/GrLumaColorFilterEffect.cpp",
    "src/gpu/effects/GrMagnifierEffect.cpp",
    "src/gpu/effects/GrMatrixConvolutionEffect.cpp",
    "src/gpu/effects/GrOvalEffect.cpp",
    "src/gpu/effects/GrPorterDuffXferProcessor.cpp",
    "src/gpu/effects/GrPremulInputFragmentProcessor.cpp",
    "src/gpu/effects/GrRectBlurEffect.cpp",
    "src/gpu/effects/GrRRectBlurEffect.cpp",
    "src/gpu/effects/GrRRectEffect.cpp",
    "src/gpu/effects/GrShadowGeoProc.cpp",
    "src/gpu/effects/GrSimpleTextureEffect.cpp",
    "src/gpu/effects/GrSkSLFP.cpp",
    "src/gpu/effects/GrSRGBEffect.cpp",
    "src/gpu/effects/GrTextureDomain.cpp",
    "src/gpu/effects/GrXfermodeFragmentProcessor.cpp",
    "src/gpu/effects/GrYUVtoRGBEffect.cpp",
    "src/gpu/gradients/GrDualIntervalGradientColorizer.cpp",
    "src/gpu/gradients/GrSingleIntervalGradientColorizer.cpp",
    "src/gpu/gradients/GrTextureGradientColorizer.cpp",
    "src/gpu/gradients/GrUnrolledBinaryGradientColorizer.cpp",
    "src/gpu/gradients/GrLinearGradientLayout.cpp",
    "src/gpu/gradients/GrRadialGradientLayout.cpp",
    "src/gpu/gradients/GrSweepGradientLayout.cpp",
    "src/gpu/gradients/GrTwoPointConicalGradientLayout.cpp",
    "src/gpu/gradients/GrClampedGradientEffect.cpp",
    "src/gpu/gradients/GrTiledGradientEffect.cpp",
    "src/gpu/gradients/GrGradientBitmapCache.cpp",
    "src/gpu/gradients/GrGradientShader.cpp",
    "src/gpu/text/GrAtlasManager.cpp",
    "src/gpu/text/GrDistanceFieldAdjustTable.cpp",
    "src/gpu/text/GrSDFMaskFilter.cpp",
    "src/gpu/text/GrStrikeCache.cpp",
    "src/gpu/text/GrTextBlob.cpp",
    "src/gpu/text/GrTextBlobCache.cpp",
    "src/gpu/text/GrTextContext.cpp",
    "src/gpu/text/GrTextBlobVertexRegenerator.cpp",
    "src/gpu/gl/GrGLAssembleInterface.cpp",
    "src/gpu/gl/GrGLBuffer.cpp",
    "src/gpu/gl/GrGLCaps.cpp",
    "src/gpu/gl/GrGLContext.cpp",
    "src/gpu/gl/GrGLCreateNullInterface.cpp",
    "src/gpu/gl/GrGLGLSL.cpp",
    "src/gpu/gl/GrGLGpu.cpp",
    "src/gpu/gl/GrGLGpuCommandBuffer.cpp",
    "src/gpu/gl/GrGLGpuProgramCache.cpp",
    "src/gpu/gl/GrGLExtensions.cpp",
    "src/gpu/gl/GrGLInterface.cpp",
    "src/gpu/gl/GrGLProgram.cpp",
    "src/gpu/gl/GrGLProgramDataManager.cpp",
    "src/gpu/gl/GrGLRenderTarget.cpp",
    "src/gpu/gl/GrGLSemaphore.cpp",
    "src/gpu/gl/GrGLStencilAttachment.cpp",
    "src/gpu/gl/GrGLTestInterface.cpp",
    "src/gpu/gl/GrGLTexture.cpp",
    "src/gpu/gl/GrGLTextureRenderTarget.cpp",
    "src/gpu/gl/GrGLUtil.cpp",
    "src/gpu/gl/GrGLUniformHandler.cpp",
    "src/gpu/gl/GrGLVaryingHandler.cpp",
    "src/gpu/gl/GrGLVertexArray.cpp",
    "src/gpu/gl/builders/GrGLProgramBuilder.cpp",
    "src/gpu/gl/builders/GrGLShaderStringBuilder.cpp",
    "src/gpu/glsl/GrGLSL.cpp",
    "src/gpu/glsl/GrGLSLBlend.cpp",
    "src/gpu/glsl/GrGLSLFragmentProcessor.cpp",
    "src/gpu/glsl/GrGLSLFragmentShaderBuilder.cpp",
    "src/gpu/glsl/GrGLSLGeometryProcessor.cpp",
    "src/gpu/glsl/GrGLSLPrimitiveProcessor.cpp",
    "src/gpu/glsl/GrGLSLProgramBuilder.cpp",
    "src/gpu/glsl/GrGLSLProgramDataManager.cpp",
    "src/gpu/glsl/GrGLSLShaderBuilder.cpp",
    "src/gpu/glsl/GrGLSLUtil.cpp",
    "src/gpu/glsl/GrGLSLVarying.cpp",
    "src/gpu/glsl/GrGLSLVertexGeoBuilder.cpp",
    "src/gpu/glsl/GrGLSLXferProcessor.cpp",
    "src/gpu/SkGpuDevice.cpp",
    "src/gpu/SkGpuDevice_drawTexture.cpp",
    "src/gpu/SkGr.cpp",
    "src/image/SkImage_Gpu.cpp",
    "src/image/SkImage_GpuBase.cpp",
    "src/image/SkImage_GpuYUVA.cpp",
    "src/image/SkSurface_Gpu.cpp",
    "src/gpu/GrPath.cpp",
    "src/gpu/GrPathProcessor.cpp",
    "src/gpu/GrPathRendering.cpp",
    "src/gpu/gl/GrGLPath.cpp",
    "src/gpu/gl/GrGLPathRendering.cpp",
    "src/gpu/ops/GrDrawPathOp.cpp",
    "src/gpu/ops/GrStencilAndCoverPathRenderer.cpp",
    "src/gpu/ops/GrStencilPathOp.cpp",
    "src/gpu/ccpr/GrCCAtlas.cpp",
    "src/gpu/ccpr/GrCCClipPath.cpp",
    "src/gpu/ccpr/GrCCClipProcessor.cpp",
    "src/gpu/ccpr/GrCCConicShader.cpp",
    "src/gpu/ccpr/GrCCCoverageProcessor.cpp",
    "src/gpu/ccpr/GrCCCoverageProcessor_GSImpl.cpp",
    "src/gpu/ccpr/GrCCCoverageProcessor_VSImpl.cpp",
    "src/gpu/ccpr/GrCCCubicShader.cpp",
    "src/gpu/ccpr/GrCCDrawPathsOp.cpp",
    "src/gpu/ccpr/GrCCFiller.cpp",
    "src/gpu/ccpr/GrCCFillGeometry.cpp",
    "src/gpu/ccpr/GrCCPathCache.cpp",
    "src/gpu/ccpr/GrCCPathProcessor.cpp",
    "src/gpu/ccpr/GrCCPerFlushResources.cpp",
    "src/gpu/ccpr/GrCCQuadraticShader.cpp",
    "src/gpu/ccpr/GrCCStrokeGeometry.cpp",
    "src/gpu/ccpr/GrCCStroker.cpp",
    "src/gpu/ccpr/GrCoverageCountingPathRenderer.cpp",
    "src/gpu/mock/GrMockGpu.cpp",
]

_IMAGE_FILTERS_SRCS = [
    "src/effects/imagefilters/SkAlphaThresholdFilter.cpp",
    "src/effects/imagefilters/SkArithmeticImageFilter.cpp",
    "src/effects/imagefilters/SkBlurImageFilter.cpp",
    "src/effects/imagefilters/SkColorFilterImageFilter.cpp",
    "src/effects/imagefilters/SkComposeImageFilter.cpp",
    "src/effects/imagefilters/SkDisplacementMapEffect.cpp",
    "src/effects/imagefilters/SkDropShadowImageFilter.cpp",
    "src/effects/imagefilters/SkImageSource.cpp",
    "src/effects/imagefilters/SkLightingImageFilter.cpp",
    "src/effects/imagefilters/SkMagnifierImageFilter.cpp",
    "src/effects/imagefilters/SkMatrixConvolutionImageFilter.cpp",
    "src/effects/imagefilters/SkMergeImageFilter.cpp",
    "src/effects/imagefilters/SkMorphologyImageFilter.cpp",
    "src/effects/imagefilters/SkOffsetImageFilter.cpp",
    "src/effects/imagefilters/SkPaintImageFilter.cpp",
    "src/effects/imagefilters/SkPictureImageFilter.cpp",
    "src/effects/imagefilters/SkTileImageFilter.cpp",
    "src/effects/imagefilters/SkXfermodeImageFilter.cpp",
]

_PORTS_SRCS = [
    "src/images/SkImageEncoder.cpp",
    "src/codec/SkBmpBaseCodec.cpp",
    "src/codec/SkBmpCodec.cpp",
    "src/codec/SkBmpMaskCodec.cpp",
    "src/codec/SkBmpRLECodec.cpp",
    "src/codec/SkBmpStandardCodec.cpp",
    "src/codec/SkCodec.cpp",
    "src/codec/SkCodecImageGenerator.cpp",
    "src/codec/SkColorTable.cpp",
    "src/codec/SkEncodedInfo.cpp",
    "src/codec/SkGifCodec.cpp",
    "src/codec/SkMaskSwizzler.cpp",
    "src/codec/SkMasks.cpp",
    "src/codec/SkSampledCodec.cpp",
    "src/codec/SkSampler.cpp",
    "src/codec/SkStreamBuffer.cpp",
    "src/codec/SkSwizzler.cpp",
    "src/codec/SkWbmpCodec.cpp",
    "src/ports/SkGlobalInitialization_default.cpp",
    "src/ports/SkImageGenerator_skia.cpp",
    "src/ports/SkMemory_malloc.cpp",
    "src/ports/SkOSFile_stdio.cpp",
    "src/sfnt/SkOTTable_name.cpp",
    "src/sfnt/SkOTUtils.cpp",
] + [
    "src/codec/SkIcoCodec.cpp",
    "src/codec/SkPngCodec.cpp",
    "src/images/SkPngEncoder.cpp",
]

_PORTS_POSIX_SRCS = [
    "src/ports/SkDebug_stdio.cpp",
    "src/ports/SkOSFile_posix.cpp",
]

_PORTS_LINUX_SRC = [
    "src/gpu/gl/glx/GrGLMakeNativeInterface_glx.cpp",
]

_PORTS_WIN_SRCS = [
    "src/gpu/gl/win/GrGLMakeNativeInterface_win.cpp",
    "src/ports/SkDebug_win.cpp",
    "src/ports/SkOSFile_win.cpp",
    "src/ports/SkImageEncoder_WIC.cpp",
    "src/ports/SkImageGeneratorWIC.cpp",
]

_PORTS_MACOS_SRCS = [
    "src/gpu/gl/mac/GrGLMakeNativeInterface_mac.cpp",
    "src/ports/SkFontHost_mac.cpp",
    "src/ports/SkImageEncoder_CG.cpp",
    "src/ports/SkImageGeneratorCG.cpp",
]

_SKSL_SRCS = [
    "src/sksl/SkSLCFGGenerator.cpp",
    "src/sksl/SkSLCompiler.cpp",
    "src/sksl/SkSLCPPCodeGenerator.cpp",
    "src/sksl/SkSLCPPUniformCTypes.cpp",
    "src/sksl/SkSLGLSLCodeGenerator.cpp",
    "src/sksl/SkSLHCodeGenerator.cpp",
    "src/sksl/SkSLInterpreter.cpp",
    "src/sksl/SkSLIRGenerator.cpp",
    "src/sksl/SkSLJIT.cpp",
    "src/sksl/SkSLLexer.cpp",
    "src/sksl/SkSLMetalCodeGenerator.cpp",
    "src/sksl/SkSLParser.cpp",
    "src/sksl/SkSLPipelineStageCodeGenerator.cpp",
    "src/sksl/SkSLSPIRVCodeGenerator.cpp",
    "src/sksl/SkSLString.cpp",
    "src/sksl/SkSLUtil.cpp",
    "src/sksl/ir/SkSLSymbolTable.cpp",
    "src/sksl/ir/SkSLSetting.cpp",
    "src/sksl/ir/SkSLType.cpp",
    "src/sksl/ir/SkSLVariableReference.cpp",
]

_THIRD_PARTY_SRCS = [
    "third_party/gif/SkGifImageReader.cpp",
    "third_party/skcms/skcms.cc",
]

_UTILS_SRCS = [
    "src/utils/Sk3D.cpp",
    "src/utils/SkAnimCodecPlayer.cpp",
    "src/utils/SkBase64.cpp",
    "src/utils/SkCamera.cpp",
    "src/utils/SkCanvasStack.cpp",
    "src/utils/SkCanvasStateUtils.cpp",
    "src/utils/SkDashPath.cpp",
    "src/utils/SkEventTracer.cpp",
    "src/utils/SkFloatToDecimal.cpp",
    "src/utils/SkFrontBufferedStream.cpp",
    "src/utils/SkInterpolator.cpp",
    "src/utils/SkJSON.cpp",
    "src/utils/SkJSONWriter.cpp",
    "src/utils/SkMatrix22.cpp",
    "src/utils/SkMultiPictureDocument.cpp",
    "src/utils/SkNWayCanvas.cpp",
    "src/utils/SkNullCanvas.cpp",
    "src/utils/SkOSPath.cpp",
    "src/utils/SkPaintFilterCanvas.cpp",
    "src/utils/SkParse.cpp",
    "src/utils/SkParseColor.cpp",
    "src/utils/SkParsePath.cpp",
    "src/utils/SkPatchUtils.cpp",
    "src/utils/SkPolyUtils.cpp",
    "src/utils/SkShadowTessellator.cpp",
    "src/utils/SkShadowUtils.cpp",
    "src/utils/SkTextUtils.cpp",
    "src/utils/SkThreadUtils_pthread.cpp",
    "src/utils/SkThreadUtils_win.cpp",
    "src/utils/SkUTF.cpp",
    "src/utils/SkWhitelistTypefaces.cpp",
    "src/utils/mac/SkCreateCGImageRef.cpp",
    "src/utils/mac/SkStream_mac.cpp",
    "src/utils/win/SkAutoCoInitialize.cpp",
    "src/utils/win/SkDWrite.cpp",
    "src/utils/win/SkDWriteFontFileStream.cpp",
    "src/utils/win/SkDWriteGeometrySink.cpp",
    "src/utils/win/SkHRESULT.cpp",
    "src/utils/win/SkIStream.cpp",
    "src/utils/win/SkWGL_win.cpp",
]

_COMMON_INCL = [ "-Iexternal/skia/" + i for i in [
    "include/c",
    "include/codec",
    "include/config",
    "include/core",
    "include/effects",
    "include/encode",
    "include/gpu",
    "include/pathops",
    "include/ports",
    "include/private",
    "include/utils",
    "src/codec",
    "src/core",
    "src/gpu",
    "src/image",
    "src/images",
    "src/opts",
    "src/sfnt",
    "src/shaders",
    "src/shaders/gradients",
    "src/sksl",
    "src/utils",
    "third_party/gif",
]]

_MACOS_INCL = [ "-Iexternal/skia/" + i for i in [
    "include/utils/mac"
]]

ALL_HDRS = glob([
    "include/**/*.h",
    "src/**/*.h",
    "src/**/*.inc",
    "third_party/**/*.h",
])

cc_library(
    name = "opts_sse2",
    srcs = glob([ "src/opts/*_SSE2.cpp", "src/opts/*_sse2.cpp" ]) + ALL_HDRS,
    copts = cc_copts() + _COMMON_INCL + ["-msse2"],
)

cc_library(
    name = "opts_ssse3",
    srcs = glob([ "src/opts/*_SSSE3.cpp", "src/opts/*_ssse3.cpp" ]) + ALL_HDRS,
    copts = cc_copts() + _COMMON_INCL + ["-mssse3"],
)

cc_library(
    name = "opts_sse41",
    srcs = glob([ "src/opts/*_sse41.cpp" ]) + ALL_HDRS,
    copts = cc_copts() + _COMMON_INCL + ["-msse4.1"],
)

cc_library(
    name = "opts_sse42",
    srcs = glob([ "src/opts/*_sse42.cpp" ]) + ALL_HDRS,
    copts = cc_copts() + _COMMON_INCL + ["-msse4.2"],
)

cc_library(
    name = "opts_avx",
    srcs = glob([ "src/opts/*_avx.cpp" ]) + ALL_HDRS,
    copts = cc_copts() + _COMMON_INCL + ["-mavx"],
)

cc_library(
    name = "opts_hsw",
    srcs = glob([ "src/opts/*_hsw.cpp" ]) + ALL_HDRS,
    copts = cc_copts() + _COMMON_INCL + ["-mavx2", "-mf16c", "-mfma"],
)

cc_library(
    name = "opts_rest",
    srcs = glob([ "src/opts/*_x86.cpp" ]) + ALL_HDRS,
    copts = cc_copts() + _COMMON_INCL,
)

cc_library(
    name = "skia",
    srcs = _CORE_SRCS + _EFFECTS_SRCS + _GPU_SRCS + _IMAGE_FILTERS_SRCS + _PORTS_SRCS + select({
        "@gapid//tools/build:linux": _PORTS_POSIX_SRCS + _PORTS_LINUX_SRC,
        "@gapid//tools/build:windows": _PORTS_WIN_SRCS,
        "@gapid//tools/build:darwin": _PORTS_POSIX_SRCS + _PORTS_MACOS_SRCS,
    }) + _SKSL_SRCS + _THIRD_PARTY_SRCS + _UTILS_SRCS + glob([
        "include/**/*.h",
        "src/**/*.h",
        "src/**/*.inc",
        "third_party/**/*.h",
    ]),
    hdrs = glob(["include/**/.h"]),
    deps = [
        ":opts_sse2",
        ":opts_ssse3",
        ":opts_sse41",
        ":opts_sse42",
        ":opts_avx",
        ":opts_hsw",
        ":opts_rest",
        "@libpng//:libpng",
        "//external:zlib",
    ],
    copts = cc_copts() + [
        "-std=c++14",
        "-DSK_HAS_PNG_LIBRARY",
    ] + _COMMON_INCL + select({
        "@gapid//tools/build:linux": [],
        "@gapid//tools/build:windows": [],
        "@gapid//tools/build:darwin": _MACOS_INCL,
    }),
    linkopts = [
        "-framework Cocoa",
        "-framework OpenGL",
    ],
    visibility = ["//visibility:public"],
)
