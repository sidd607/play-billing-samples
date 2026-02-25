/* Copyright 2022 Google LLC
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.google.play.billing.samples.onetimepurchases.utils;

import androidx.constraintlayout.motion.widget.MotionLayout;
import androidx.constraintlayout.widget.ConstraintSet;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import com.google.play.billing.samples.onetimepurchases.R;

public final class LayoutUtils {
    private LayoutUtils() {}

    public static void setupWindowInsets(MotionLayout motionLayout) {
        ViewCompat.setOnApplyWindowInsetsListener(
            motionLayout,
            (v, windowInsets) -> {
              Insets insets = windowInsets.getInsets(WindowInsetsCompat.Type.systemBars());
              int statusBarHeight = insets.top;

              ConstraintSet startSet = motionLayout.getConstraintSet(R.id.start);
              if (startSet != null) {
                  startSet.setMargin(R.id.product_list, ConstraintSet.TOP, statusBarHeight);
                  motionLayout.updateState(R.id.start, startSet);
              }

              ConstraintSet endSet = motionLayout.getConstraintSet(R.id.end);
              if (endSet != null) {
                  endSet.setMargin(R.id.product_list, ConstraintSet.TOP, statusBarHeight);
                  motionLayout.updateState(R.id.end, endSet);
              }

              return WindowInsetsCompat.CONSUMED;
            });
    }
}