/*
 * Copyright (c) 2009, 2024, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.  Oracle designates this
 * particular file as subject to the "Classpath" exception as provided
 * by Oracle in the LICENSE file that accompanied this code.
 *
 * This code is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public License
 * version 2 for more details (a copy is included in the LICENSE file that
 * accompanied this code).
 *
 * You should have received a copy of the GNU General Public License version
 * 2 along with this work; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin St, Fifth Floor, Boston, MA 02110-1301 USA.
 *
 * Please contact Oracle, 500 Oracle Parkway, Redwood Shores, CA 94065 USA
 * or visit www.oracle.com if you need additional information or have any
 * questions.
 */

package com.sun.javafx.iio;

import com.sun.javafx.PlatformUtil;
import com.sun.javafx.iio.ImageFormatDescription.Signature;
import com.sun.javafx.iio.bmp.BMPImageLoaderFactory;
import com.sun.javafx.iio.common.ImageTools;
import com.sun.javafx.iio.gif.GIFImageLoaderFactory;
import com.sun.javafx.iio.ios.IosImageLoaderFactory;
import com.sun.javafx.iio.jpeg.JPEGImageLoaderFactory;
import com.sun.javafx.iio.png.PNGImageLoaderFactory;
import com.sun.javafx.logging.PlatformLogger;
import com.sun.javafx.util.DataURI;
import com.sun.javafx.util.Logging;

import java.io.BufferedInputStream;
import java.io.ByteArrayInputStream;
import java.io.EOFException;
import java.io.IOException;
import java.io.InputStream;
import java.io.SequenceInputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map.Entry;
import java.util.Optional;

/**
 * A convenience class for simple image loading. Factories for creating loaders
 * for image formats must be registered with this class.
 */
public class ImageStorage {

    /**
     * An enumeration of supported image types.
     */
    public static enum ImageType {

        /**
         * An image with a single channel of 8-bit valued gray levels.
         */
        GRAY,
        /**
         * An image with two 8-bit valued channels, one of gray levels,
         * the other of non-premultiplied opacity, ordered as GAGAGA...
         */
        GRAY_ALPHA,
        /**
         * An image with two 8-bit valued channels, one of gray levels,
         * the other of premultiplied opacity, ordered as GAGAGA...
         */
        GRAY_ALPHA_PRE,
        /**
         * An image with one channel of indexes into a 24-bit
         * lookup table which maps the indexes to 8-bit RGB components.
         */
        PALETTE,
        /**
         * An image with one channel of indexes into a 32-bit
         * lookup table which maps the indexes to 8-bit RGBA components
         * wherein the opacity is not-premultiplied.
         */
        PALETTE_ALPHA,
        /**
         * An image with one channel of indexes into a 32-bit
         * lookup table which maps the indexes to 8-bit RGBA components
         * wherein the opacity is premultiplied.
         */
        PALETTE_ALPHA_PRE,
        /**
         * An image with one channel of indexes into a 24-bit
         * lookup table which maps the indexes to 8-bit RGB components, and
         * a single transparent index to indicate the location of transparent
         * pixels.
         */
        PALETTE_TRANS,
        /**
         * An image with three 8-bit valued channels of red, green, and
         * blue, respectively, ordered as RGBRGBRGB...
         */
        RGB,
        /**
         * An image with three 8-bit valued channels of red, green, and
         * blue, respectively, ordered as BGRBGRBGR...
         */
        BGR,
        /**
         * An image with four 8-bit valued channels of red, green, blue,
         * and non-premultiplied opacity, respectively, ordered as
         * RGBARGBARGBA...
         */
        RGBA,
        /**
         * An image with four 8-bit valued channels of red, green, blue,
         * and premultiplied opacity, respectively, ordered as
         * RGBARGBARGBA...
         */
        RGBA_PRE,
        /**
         * An image with four 8-bit valued channels of red, green, blue,
         * and non-premultiplied opacity, respectively, ordered as
         * BGRABGRABGRA...
         */
        BGRA,
        /**
         * An image with four 8-bit valued channels of red, green, blue,
         * and premultiplied opacity, respectively, ordered as
         * BGRABGRABGRA...
         */
        BGRA_PRE,
        /**
         * An image with four 8-bit valued channels of red, green, blue,
         * and non-premultiplied opacity, respectively, ordered as
         * ABGRABGRABGR...
         */
        ABGR,
        /**
         * An image with four 8-bit valued channels of red, green, blue,
         * and premultiplied opacity, respectively, ordered as
         * ABGRABGRABGR...
         */
        ABGR_PRE,
        /**
         * An image with three 8-bit valued channels of red, green, and blue,
         * packed into a 32-bit integer, ordered as XRGBXRGBXRGB...
         */
        INT_RGB,
        /**
         * An image with three 8-bit valued channels of red, green, and blue,
         * packed into a 32-bit integer, ordered as XBGRXBGRXBGR...
         */
        INT_BGR,
        /**
         * An image with four 8-bit valued channels of red, green, blue, and
         * non-premultiplied opacity, packed into a 32-bit integer, ordered as
         * ARGBARGBARGB...
         */
        INT_ARGB,
        /**
         * An image with four 8-bit valued channels of red, green, blue, and
         * premultiplied opacity, packed into a 32-bit integer, ordered as
         * ARGBARGBARGB...
         */
        INT_ARGB_PRE
    }
//    /**
//     * A mapping of lower case file extensions to loader factories.
//     */
//    private static HashMap<String, ImageLoaderFactory> loaderFactoriesByExtension;
    /**
     * A mapping of format signature byte sequences to loader factories.
     */
    private final HashMap<Signature, ImageLoaderFactory> loaderFactoriesBySignature;
    /**
     * A mapping of lower case MIME subtypes to loader factories.
     */
    private final HashMap<String, ImageLoaderFactory> loaderFactoriesByMimeSubtype;
    private final ImageLoaderFactory[] loaderFactories;
    private Optional<ImageLoaderFactory> j2dImageLoaderFactory;
    private int maxSignatureLength;

    private static final boolean isIOS = PlatformUtil.isIOS();

    private static class InstanceHolder {
        static final ImageStorage INSTANCE = new ImageStorage();
    }

    public static ImageStorage getInstance() {
        return InstanceHolder.INSTANCE;
    }

    public ImageStorage() {
        if (isIOS) {
            //On iOS we have single factory/ native loader
            //for all image formats
            loaderFactories = new ImageLoaderFactory[]{
                IosImageLoaderFactory.getInstance()
            };
        } else {
            loaderFactories = new ImageLoaderFactory[]{
                GIFImageLoaderFactory.getInstance(),
                JPEGImageLoaderFactory.getInstance(),
                PNGImageLoaderFactory.getInstance(),
                BMPImageLoaderFactory.getInstance()
                // Note: append ImageLoadFactory for any new format here.
            };
        }

//        loaderFactoriesByExtension = new HashMap(numExtensions);
        loaderFactoriesBySignature = new HashMap<>(loaderFactories.length);
        loaderFactoriesByMimeSubtype = new HashMap<>(loaderFactories.length);

        for (int i = 0; i < loaderFactories.length; i++) {
            addImageLoaderFactory(loaderFactories[i]);
        }
    }

    public ImageFormatDescription[] getSupportedDescriptions() {
        ImageFormatDescription[] formats = new ImageFormatDescription[loaderFactories.length];
        for (int i = 0; i < loaderFactories.length; i++) {
            formats[i] = loaderFactories[i].getFormatDescription();
        }
        return (formats);
    }

    /**
     * Returns the number of bands for a raw image of the specified type.
     *
     * @param type the type of image
     * @return the number of bands of a raw image of this type
     */
    public int getNumBands(ImageType type) {
        int numBands = -1;
        switch (type) {
            case GRAY:
            case PALETTE:
            case PALETTE_ALPHA:
            case PALETTE_ALPHA_PRE:
            case PALETTE_TRANS:
                numBands = 1;
                break;
            case GRAY_ALPHA:
            case GRAY_ALPHA_PRE:
                numBands = 2;
                break;
            case RGB:
                numBands = 3;
                break;
            case RGBA:
            case RGBA_PRE:
                numBands = 4;
                break;
            default:
                throw new IllegalArgumentException("Unknown ImageType " + type);
        }
        return numBands;
    }

    /**
     * Registers an image loader factory. The factory replaces any other factory
     * previously registered for the file extensions (converted to lower case),
     * MIME subtype, and signature indicated by the format description.
     *
     * @param factory the factory to register.
     */
    public void addImageLoaderFactory(ImageLoaderFactory factory) {
        ImageFormatDescription desc = factory.getFormatDescription();
//        String[] extensions = desc.getExtensions();
//        for (int j = 0; j < extensions.length; j++) {
//            loaderFactoriesByExtension.put(extensions[j].toLowerCase(), factory);
//        }

        for (final Signature signature: desc.getSignatures()) {
            loaderFactoriesBySignature.put(signature, factory);
        }

        for (String subtype : desc.getMIMESubtypes()) {
            loaderFactoriesByMimeSubtype.put(subtype.toLowerCase(), factory);
        }

        // invalidate max signature length
        synchronized (ImageStorage.class) {
            maxSignatureLength = -1;
        }
    }

    /**
     * Load all images present in the specified stream. The image will be
     * rescaled according to this algorithm:
     *
     * <code><pre>
     * int finalWidth, finalHeight; // final dimensions
     * int width, height;     // specified maximum dimensions
     * // Use source dimensions as default values.
     * if (width <= 0) {
     *     width = sourceWidth;
     * }
     * if (height <= 0) {
     *     height = sourceHeight;
     * }
     * // If not downscaling reset the dimensions to those of the source.
     * if (!((width < sourceWidth && height <= sourceHeight) ||
     *       (width <= sourceWidth && height < sourceHeight))) {
     *      finalWidth = sourceWidth;
     *      finalHeight = sourceHeight;
     * } else if(preserveAspectRatio) {
     *      double r = (double) sourceWidth / (double) sourceHeight;
     *      finalHeight = (int) ((width / r < height ? width / r : height) + 0.5);
     *      finalWidth = (int) (r * finalHeight + 0.5);
     * } else {
     *      finalWidth = width;
     *      finalHeight = height;
     * }
     * </pre></code>
     *
     * @param input the image data stream.
     * @param listener a listener to receive notifications about image loading.
     * @param width the desired width of the image; if non-positive,
     * the original image width will be used.
     * @param height the desired height of the image; if non-positive, the
     * original image height will be used.
     * @param preserveAspectRatio whether to preserve the width-to-height ratio
     * of the image.
     * @param smooth whether to apply smoothing when downsampling.
     * @return the sequence of all images in the specified source or
     * <code>null</code> on error.
     */
    public ImageFrame[] loadAll(InputStream input, ImageLoadListener listener,
            double width, double height, boolean preserveAspectRatio,
            float pixelScale, boolean smooth) throws ImageStorageException {
        ImageLoader loader = null;
        ImageFrame[] images = null;

        try {
            loader = findImageLoader(input, listener);

            if (loader != null) {
                // Images loaded from an InputStream always have an image pixel scale of 1, since we
                // don't have a file name to infer a different intrinsic scale (with the @Nx convention).
                images = loadAll(loader, width, height, preserveAspectRatio, pixelScale, 1, smooth);
            } else {
                throw new ImageStorageException("No loader for image data");
            }
        } catch (ImageStorageException ise) {
            throw ise;
        } catch (IOException e) {
            throw new ImageStorageException(e.getMessage(), e);
        } finally {
            if (loader != null) {
                loader.dispose();
            }
        }
        return images;
    }

    /**
     * Load all images present in the specified input. For more details refer to
     * {@link #loadAll(InputStream, ImageLoadListener, double, double, boolean, float, boolean)}.
     */
    public ImageFrame[] loadAll(String input, ImageLoadListener listener,
            double width, double height, boolean preserveAspectRatio,
            float devPixelScale, boolean smooth) throws ImageStorageException {

        if (input == null || input.isEmpty()) {
            throw new ImageStorageException("URL can't be null or empty");
        }

        ImageFrame[] images = null;
        InputStream theStream = null;
        ImageLoader loader = null;

        try {
            float imgPixelScale = 1.0f;
            try {
                DataURI dataUri = DataURI.tryParse(input);
                if (dataUri != null) {
                    if (!"image".equalsIgnoreCase(dataUri.getMimeType())) {
                        throw new IllegalArgumentException("Unexpected MIME type: " + dataUri.getMimeType());
                    }

                    // Find a factory that can load images with the specified MIME type.
                    var factory = loaderFactoriesByMimeSubtype.get(dataUri.getMimeSubtype().toLowerCase());
                    if (factory != null) {
                        // We also inspect the image file signature to confirm that it matches the MIME type.
                        theStream = new ByteArrayInputStream(dataUri.getData());
                        ImageLoader loaderBySignature = getLoaderBySignature(theStream, listener);

                        if (loaderBySignature != null) {
                            // If the MIME type doesn't agree with the file signature, log a warning and
                            // continue with the image loader that matches the file signature.
                            boolean imageTypeMismatch = !factory.getFormatDescription().getFormatName().equals(
                                loaderBySignature.getFormatDescription().getFormatName());

                            if (imageTypeMismatch) {
                                var logger = Logging.getJavaFXLogger();
                                if (logger.isLoggable(PlatformLogger.Level.WARNING)) {
                                    logger.warning(String.format(
                                        "Image format '%s' does not match MIME type '%s/%s' in URI '%s'",
                                        loaderBySignature.getFormatDescription().getFormatName(),
                                        dataUri.getMimeType(), dataUri.getMimeSubtype(), dataUri));
                                }
                            }

                            loader = loaderBySignature;
                        } else {
                            // We're here because the image format doesn't have a detectable signature.
                            // In this case, we need to close the input stream (because we already consumed
                            // parts of it to detect a potential file signature) and create a new input
                            // stream for the image loader that matches the MIME type.
                            theStream.close();
                            theStream = new ByteArrayInputStream(dataUri.getData());
                            loader = factory.createImageLoader(theStream);
                        }
                    } else {
                        // If we don't have a built-in loader factory we try to find an ImageIO loader
                        // that can load the content of the data URI.
                        ImageLoader imageLoader = tryCreateJ2DImageLoader(new ByteArrayInputStream(dataUri.getData()));

                        if (imageLoader == null) {
                            throw new IllegalArgumentException(
                                "Unsupported MIME subtype: image/" + dataUri.getMimeSubtype());
                        }

                        // If the specified MIME type doesn't agree with any of the supported MIME types of
                        // the J2DImageLoader, we log a warning but continue to load the image.
                        boolean imageTypeMismatch = imageLoader.getFormatDescription().getMIMESubtypes().stream()
                                .noneMatch(dataUri.getMimeSubtype()::equals);

                        if (imageTypeMismatch) {
                            var logger = Logging.getJavaFXLogger();
                            if (logger.isLoggable(PlatformLogger.Level.WARNING)) {
                                logger.warning(String.format(
                                    "Image format '%s' does not match MIME type '%s/%s' in URI '%s'",
                                    imageLoader.getFormatDescription().getFormatName(),
                                    dataUri.getMimeType(), dataUri.getMimeSubtype(), dataUri));
                            }
                        }

                        loader = imageLoader;
                    }
                } else {
                    // Use Mac Retina conventions for >= 1.5f (rounded to the next integer scale)
                    // first, check if the scale is not already requested in the input
                    if (ImageTools.hasScaledName(input)) {
                        // scaled name exists, assume user explicitly wants it and attempt loading
                        // if we can't find the resource this should throw and cancel the load
                        theStream = ImageTools.createInputStream(input);
                    }

                    if (theStream == null) {
                        // not the case, find the highest available scale
                        for (int imageScale = Math.round(devPixelScale); imageScale >= 2; --imageScale) {
                            try {
                                String scaledName = ImageTools.getScaledImageName(input, imageScale);
                                theStream = ImageTools.createInputStream(scaledName);
                                imgPixelScale = imageScale;
                                break;
                            } catch (IOException ignored) {
                            }
                        }
                    }

                    IOException mainException = null;
                    if (theStream == null) {
                        try {
                            theStream = ImageTools.createInputStream(input);
                        } catch (IOException e) {
                            // hold on to this exception for a moment, in case below fallback fails too
                            mainException = e;
                        }
                    }

                    if (theStream == null) {
                        try {
                            // last fallback, try to see if the file exists with @1x suffix
                            String scaled1xName = ImageTools.getScaledImageName(input, 1);
                            theStream = ImageTools.createInputStream(scaled1xName);
                        } catch (IOException e) {
                            // fallback failed, throw previous exception with this one as suppressed
                            mainException.addSuppressed(e);
                            throw mainException;
                        }
                    }

                    loader = findImageLoader(theStream, listener);
                }
            } catch (Exception e) {
                throw new ImageStorageException(e.getMessage(), e);
            }

            if (loader != null) {
                images = loadAll(loader, width, height, preserveAspectRatio, devPixelScale, imgPixelScale, smooth);
            } else {
                throw new ImageStorageException("No loader for image data");
            }
        } finally {
            if (loader != null) {
                loader.dispose();
            }
            try {
                if (theStream != null) {
                    theStream.close();
                }
            } catch (IOException ignored) {
            }
        }

        return images;
    }

    private synchronized int getMaxSignatureLength() {
        if (maxSignatureLength < 0) {
            maxSignatureLength = 0;
            for (final Signature signature:
                    loaderFactoriesBySignature.keySet()) {
                final int signatureLength = signature.getLength();
                if (maxSignatureLength < signatureLength) {
                    maxSignatureLength = signatureLength;
                }
            }
        }

        return maxSignatureLength;
    }

    private ImageFrame[] loadAll(ImageLoader loader,
            double width, double height, boolean preserveAspectRatio,
            float devPixelScale, float imgPixelScale, boolean smooth) throws ImageStorageException {
        ImageFrame[] images = null;
        ArrayList<ImageFrame> list = new ArrayList<>();
        int imageIndex = 0;
        ImageFrame image = null;
        do {
            try {
                image = loader.load(imageIndex++, width, height, preserveAspectRatio, smooth, devPixelScale, imgPixelScale);
            } catch (Exception e) {
                // allow partially loaded animated images
                if (imageIndex > 1) {
                    break;
                } else {
                    throw new ImageStorageException(e.getMessage(), e);
                }
            }
            if (image != null) {
                list.add(image);
            } else {
                break;
            }
        } while (true);
        int numImages = list.size();
        if (numImages > 0) {
            images = new ImageFrame[numImages];
            list.toArray(images);
        }
        return images;
    }

    private ImageLoader findImageLoader(InputStream stream, ImageLoadListener listener) throws IOException {
        if (isIOS) {
            return IosImageLoaderFactory.getInstance().createImageLoader(stream);
        }

        // We need a stream that supports the mark and reset methods, since J2DImageLoader
        // is used as a fallback after our built-in loader selection has already consumed
        // part of the input stream.
        if (!stream.markSupported()) {
            stream = new BufferedInputStream(stream);
        }

        stream.mark(Integer.MAX_VALUE);
        ImageLoader loader = getLoaderBySignature(stream, listener);

        if (loader == null) {
            stream.reset();
            loader = tryCreateJ2DImageLoader(stream);
        }

        return loader;
    }

//    private static ImageLoader getLoaderByExtension(String input, ImageLoadListener listener) {
//        ImageLoader loader = null;
//
//        int dotIndex = input.lastIndexOf(".");
//        if (dotIndex != -1) {
//            String extension = input.substring(dotIndex + 1).toLowerCase();
//            Set extensions = loaderFactoriesByExtension.keySet();
//            if (extensions.contains(extension)) {
//                ImageLoaderFactory factory = loaderFactoriesByExtension.get(extension);
//                InputStream stream = ImageTools.createInputStream(input);
//                if (stream != null) {
//                    loader = factory.createImageLoader(stream);
//                    if (listener != null) {
//                        loader.addListener(listener);
//                    }
//                }
//            }
//        }
//
//        return loader;
//    }

    private ImageLoader getLoaderBySignature(InputStream stream, ImageLoadListener listener) throws IOException {
        byte[] header = new byte[getMaxSignatureLength()];

        try {
            ImageTools.readFully(stream, header);
        } catch (EOFException ignored) {
            return null;
        }

        for (final Entry<Signature, ImageLoaderFactory> factoryRegistration:
                 loaderFactoriesBySignature.entrySet()) {
            if (factoryRegistration.getKey().matches(header)) {
                InputStream headerStream = new ByteArrayInputStream(header);
                InputStream seqStream = new SequenceInputStream(headerStream, stream);
                ImageLoader loader = factoryRegistration.getValue().createImageLoader(seqStream);
                if (listener != null) {
                    loader.addListener(listener);
                }

                return loader;
            }
        }

        // not found
        return null;
    }

    /**
     * Tries to create an {@link com.sun.javafx.iio.java2d.J2DImageLoader} for the specified input stream.
     * This might fail in the future if the {@code java.desktop} module is not present on the module path.
     * At present, this will not fail because JavaFX requires the {@code java.desktop} module.
     */
    private synchronized ImageLoader tryCreateJ2DImageLoader(InputStream stream) throws IOException {
        if (j2dImageLoaderFactory == null) {
            try {
                Class<?> factoryClass = Class.forName("com.sun.javafx.iio.java2d.J2DImageLoaderFactory");
                j2dImageLoaderFactory = Optional.of((ImageLoaderFactory)factoryClass.getMethod("getInstance").invoke(null));
            } catch (NoClassDefFoundError | ReflectiveOperationException e) {
                j2dImageLoaderFactory = Optional.empty();
            }
        }

        if (j2dImageLoaderFactory.isEmpty()) {
            return null;
        }

        return j2dImageLoaderFactory.get().createImageLoader(stream);
    }
}
