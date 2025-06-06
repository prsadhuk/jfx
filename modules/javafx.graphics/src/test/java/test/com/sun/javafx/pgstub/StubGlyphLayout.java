/*
 * Copyright (c) 2025, Oracle and/or its affiliates. All rights reserved.
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
package test.com.sun.javafx.pgstub;

import com.sun.javafx.font.CharToGlyphMapper;
import com.sun.javafx.font.FontResource;
import com.sun.javafx.font.FontStrike;
import com.sun.javafx.font.PGFont;
import com.sun.javafx.text.GlyphLayout;
import com.sun.javafx.text.TextRun;

/**
 *
 */
public class StubGlyphLayout extends GlyphLayout {
    public StubGlyphLayout() {
    }

    @Override
    public void dispose() {
    }

    @Override
    public void layout(TextRun run, PGFont font, FontStrike strike, char[] chars) {
        FontResource fr = strike.getFontResource();
        int start = run.getStart();
        int length = run.getLength();

        // simplified code from PrismTextLayout.shape()
        float fontSize = strike.getSize();
        CharToGlyphMapper mapper = fr.getGlyphMapper();

        /* The text contains complex and non-complex runs */
        int[] glyphs = new int[length];
        mapper.charsToGlyphs(start, length, chars, glyphs);
        float[] positions = new float[(length + 1) << 1];
        float xadvance = 0;
        for (int i = 0; i < length; i++) {
            float width = fr.getAdvance(glyphs[i], fontSize);
            positions[i << 1] = xadvance;
            //yadvance always zero
            xadvance += width;
        }
        positions[length << 1] = xadvance;
        run.shape(length, glyphs, positions, null);
    }
}
