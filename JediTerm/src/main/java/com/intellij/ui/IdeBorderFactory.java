/*
 * Copyright 2000-2014 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.intellij.ui;

import com.intellij.openapi.ui.RoundedLineBorder;
import com.intellij.openapi.ui.SideBorder;
import com.intellij.ui.JBColor;
import com.intellij.util.ui.JBInsets;
import org.intellij.lang.annotations.MagicConstant;
import org.jetbrains.annotations.NotNull;

import javax.swing.border.Border;
import javax.swing.border.EmptyBorder;
import java.awt.*;

public class IdeBorderFactory {
  public static final int BORDER_ROUNDNESS = 5;
  public static final int TITLED_BORDER_TOP_INSET = 7;
  public static final int TITLED_BORDER_LEFT_INSET = 0;
  public static final int TITLED_BORDER_BOTTOM_INSET = 10;
  public static final int TITLED_BORDER_RIGHT_INSET = 0;
  public static final int TITLED_BORDER_INDENT = 20;

  private IdeBorderFactory() {
  }

  public static Border createBorder() {
    return createBorder(SideBorder.ALL);
  }

  public static Border createBorder(@MagicConstant(flagsFromClass = SideBorder.class) int borders) {
    return new SideBorder(getBorderColor(), borders);
  }

  @NotNull
  public static RoundedLineBorder createRoundedBorder() {
    return createRoundedBorder(BORDER_ROUNDNESS);
  }

  @NotNull
  public static RoundedLineBorder createRoundedBorder(int arcSize) {
    return new RoundedLineBorder(getBorderColor(), arcSize);
  }

  @NotNull
  public static RoundedLineBorder createRoundedBorder(int arcSize, final int thickness) {
    return new RoundedLineBorder(getBorderColor(), arcSize, thickness);
  }

  public static Border createEmptyBorder(Insets insets) {
    return new EmptyBorder(JBInsets.create(insets));
  }

  public static Border createEmptyBorder() {
    return createEmptyBorder(0);
  }
  public static Border createEmptyBorder(int thickness) {
    return new EmptyBorder(new JBInsets(thickness, thickness, thickness, thickness));
  }

  public static Border createEmptyBorder(int top, int left, int bottom, int right) {
    return new EmptyBorder(new JBInsets(top, left, bottom, right));
  }

  private static Color getBorderColor() {
    return JBColor.border();
  }

}
