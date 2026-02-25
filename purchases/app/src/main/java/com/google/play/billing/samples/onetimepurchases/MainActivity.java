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
package com.google.play.billing.samples.onetimepurchases;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.view.View;
import android.widget.TextView;
import androidx.appcompat.app.AppCompatActivity;
import androidx.constraintlayout.motion.widget.MotionLayout;
import androidx.constraintlayout.widget.ConstraintSet;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import com.android.billingclient.api.BillingClient.BillingResponseCode;
import com.android.billingclient.api.BillingClient.ProductType;
import com.android.billingclient.api.BillingResult;
import com.android.billingclient.api.ProductDetails;
import com.android.billingclient.api.QueryProductDetailsParams.Product;
import com.google.android.gms.oss.licenses.OssLicensesMenuActivity;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.card.MaterialCardView;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.imageview.ShapeableImageView;
import com.google.common.collect.ImmutableList;
import com.google.play.billing.samples.onetimepurchases.billing.BillingServiceClient;
import com.google.play.billing.samples.onetimepurchases.billing.BillingServiceClientListener;
import java.util.Map;
import java.util.Objects;
import java.util.HashSet;
import java.util.Set;
import java.util.ArrayList;
import android.widget.LinearLayout;
import android.view.LayoutInflater;

/** This is the main activity class */
public class MainActivity extends AppCompatActivity implements BillingServiceClientListener {

  private BillingServiceClient billingServiceClient;
  private final Set<String> selectedProductIds = new HashSet<>();
  private static final String CONSUMABLE_PRODUCT_01 = "consumable_product_01";
  private static final String CONSUMABLE_PRODUCT_02 = "consumable_product_020";
  private static final String CONSUMABLE_PRODUCT_03 = "consumable_product_03";

  private static final ImmutableList<Product> PRODUCT_LIST =
      ImmutableList.of(
          Product.newBuilder()
              .setProductId(CONSUMABLE_PRODUCT_01)
              .setProductType(ProductType.INAPP)
              .build(),
          Product.newBuilder()
              .setProductId(CONSUMABLE_PRODUCT_02)
              .setProductType(ProductType.INAPP)
              .build(),
          Product.newBuilder()
              .setProductId(CONSUMABLE_PRODUCT_03)
              .setProductType(ProductType.INAPP)
              .build());

  @Override
  protected void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
    setContentView(R.layout.activity_main);

    final MotionLayout motionLayout = findViewById(R.id.motion_layout);
    ViewCompat.setOnApplyWindowInsetsListener(
            motionLayout,
            (v, windowInsets) -> {
              Insets insets = windowInsets.getInsets(WindowInsetsCompat.Type.systemBars());
              int statusBarHeight = insets.top;

              ConstraintSet startSet = motionLayout.getConstraintSet(R.id.start);
              startSet.setMargin(R.id.product_list, ConstraintSet.TOP, statusBarHeight);
              motionLayout.updateState(R.id.start, startSet);

              ConstraintSet endSet = motionLayout.getConstraintSet(R.id.end);
              endSet.setMargin(R.id.product_list, ConstraintSet.TOP, statusBarHeight);
              motionLayout.updateState(R.id.end, endSet);

              return WindowInsetsCompat.CONSUMED;
            });

    // Setup Billing Client
    billingServiceClient = new BillingServiceClient(this, this);
    billingServiceClient.startBillingConnection(PRODUCT_LIST);

    MaterialButton buyAllButton = findViewById(R.id.buy_all_button);
    buyAllButton.setOnClickListener(v -> {
        if (!selectedProductIds.isEmpty()) {
            billingServiceClient.launchBillingFlow(new ArrayList<>(selectedProductIds));
        }
    });

  }

  public void showBillingResponseDialog(int responseCode, BillingResult billingResult) {
    final String dialogTitle =
        responseCode == BillingResponseCode.OK ? "Purchase Successful" : "Purchase Failed";
    final String dialogMessage = billingResult.toString();

    // Run on UI thread in case this is called from a background thread
    runOnUiThread(
        () -> {
          MaterialAlertDialogBuilder dialogBuilder = new MaterialAlertDialogBuilder(this);
          dialogBuilder
              .setTitle(dialogTitle)
              .setMessage(dialogMessage)
              .setPositiveButton("OK", (dialog, which) -> dialog.dismiss())
              .show();
        });
    }

  @Override
  protected void onDestroy() {
    super.onDestroy();

    // End the billing connection
    billingServiceClient.endBillingConnection();
  }

    private void updateProductCardUI(View cardView, ProductDetails productDetails) {

        String productId = productDetails.getProductId();

        // Find views within the provided cardView
        TextView titleView = cardView.findViewById(R.id.product_title);
        TextView descView = cardView.findViewById(R.id.product_description);
        TextView priceView = cardView.findViewById(R.id.product_price);
        ShapeableImageView productImageView = cardView.findViewById(R.id.product_image);

        // Update views with product details
        titleView.setText(productDetails.getName());
        descView.setText(productDetails.getDescription());

        productImageView.setImageResource(getDrawableProductImageForProductId(productId));

        String formattedPrice =
                Objects.requireNonNull(productDetails.getOneTimePurchaseOfferDetails()).getFormattedPrice();
        priceView.setText(formattedPrice);

        cardView.setOnClickListener(v -> {
            MaterialCardView card = (MaterialCardView) cardView;
            boolean isChecked = !card.isChecked();
            card.setChecked(isChecked);
            if (isChecked) {
                selectedProductIds.add(productId);
            } else {
                selectedProductIds.remove(productId);
            }
            updateBuyButton();
        });
    }

    private void updateBuyButton() {
        MaterialButton buyAllButton = findViewById(R.id.buy_all_button);
        if (selectedProductIds.isEmpty()) {
            buyAllButton.setVisibility(View.GONE);
        } else {
            buyAllButton.setVisibility(View.VISIBLE);
            buyAllButton.setText("Buy Selected (" + selectedProductIds.size() + ")");
        }
    }

  @Override
  public void onBillingResponse(int responseCode, BillingResult billingResult) {
      showBillingResponseDialog(responseCode, billingResult);
  }

  @Override
  public void onProductDetailsFetched(Map<String, ProductDetails> productDetailsMap) {
      runOnUiThread(
              () -> {
                  LinearLayout container = findViewById(R.id.dynamic_product_list);
                  TextView noProductsText = findViewById(R.id.no_products_text);
                  container.removeAllViews();
                  selectedProductIds.clear();
                  updateBuyButton();

                  if (productDetailsMap.isEmpty()) {
                      noProductsText.setVisibility(View.VISIBLE);
                  } else {
                      noProductsText.setVisibility(View.GONE);
                      LayoutInflater inflater = LayoutInflater.from(this);
                      for (ProductDetails productDetails : productDetailsMap.values()) {
                          View cardView = inflater.inflate(R.layout.product_card, container, false);
                          updateProductCardUI(cardView, productDetails);
                          container.addView(cardView);
                      }
                  }
              });
  }

  private int getDrawableProductImageForProductId(String productId) {
      return switch (productId) {
        case CONSUMABLE_PRODUCT_01 -> R.drawable.consumable_product_01;
        case CONSUMABLE_PRODUCT_02 -> R.drawable.consumable_product_02;
        case CONSUMABLE_PRODUCT_03 -> R.drawable.consumable_product_03;
        default -> 0;
      };
  }
}
