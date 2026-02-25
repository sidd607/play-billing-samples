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

import android.os.Bundle;
import android.util.Log;
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
import com.android.billingclient.api.Purchase;
import com.android.billingclient.api.QueryProductDetailsParams.Product;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.card.MaterialCardView;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.imageview.ShapeableImageView;
import com.google.common.collect.ImmutableList;
import com.google.play.billing.samples.onetimepurchases.billing.BillingServiceClient;
import com.google.play.billing.samples.onetimepurchases.billing.BillingServiceClientListener;

import java.util.List;
import java.util.Map;
import java.util.HashSet;
import java.util.Set;
import java.util.ArrayList;
import android.widget.LinearLayout;
import android.view.LayoutInflater;
import android.content.Intent;

/** This is the main activity class */
public class MainActivity extends AppCompatActivity implements BillingServiceClientListener {

  private BillingServiceClient billingServiceClient;
  private final Set<String> selectedProductIds = new HashSet<>();
  private final Set<String> purchasedProductIds = new HashSet<>();
  private Map<String, ProductDetails> cachedProductDetailsMap;
  private static final String SUBS_PRODUCT_01 = "sidd607_subs";
  private static final String SUBS_PRODUCT_02 = "sidd607_subs_premium";
  private static final String ADDON_SUBS_PRODUCT_01 = "kids_package";
    private static final String ADDON_SUBS_PRODUCT_02 = "news_package";
    private static final String ADDON_SUBS_PRODUCT_03 = "sports_package";

  private static final ImmutableList<Product> PRODUCT_LIST =
      ImmutableList.of(
          Product.newBuilder()
              .setProductId(SUBS_PRODUCT_01)
              .setProductType(ProductType.SUBS)
              .build(),
          Product.newBuilder()
              .setProductId(SUBS_PRODUCT_02)
              .setProductType(ProductType.SUBS)
              .build(),
          Product.newBuilder()
              .setProductId(ADDON_SUBS_PRODUCT_01)
              .setProductType(ProductType.SUBS)
              .build(),
          Product.newBuilder()
              .setProductId(ADDON_SUBS_PRODUCT_02)
              .setProductType(ProductType.SUBS)
              .build(),
          Product.newBuilder()
              .setProductId(ADDON_SUBS_PRODUCT_03)
              .setProductType(ProductType.SUBS)
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

    View viewPurchasesFab = findViewById(R.id.view_purchases_fab);
    viewPurchasesFab.setOnClickListener(v -> {
        Intent intent = new Intent(this, PurchasesActivity.class);
        startActivity(intent);
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
        boolean isOwned = purchasedProductIds.contains(productId);

        // Find views within the provided cardView
        TextView titleView = cardView.findViewById(R.id.product_title);
        TextView descView = cardView.findViewById(R.id.product_description);
        TextView priceView = cardView.findViewById(R.id.product_price);
        TextView periodView = cardView.findViewById(R.id.product_period);
        ShapeableImageView productImageView = cardView.findViewById(R.id.product_image);
        MaterialCardView card = (MaterialCardView) cardView;

        // Update views with product details
        titleView.setText(productDetails.getName());
        descView.setText(productDetails.getDescription().strip().replace("\n", ""));

        productImageView.setImageResource(getDrawableProductImageForProductId(productId));

        if (isOwned) {
            priceView.setText("Subscribed");
            priceView.setTextColor(getResources().getColor(android.R.color.holo_green_dark, getTheme()));
            periodView.setVisibility(View.GONE);
            card.setCheckable(false);
            card.setClickable(false);
            card.setAlpha(0.7f); // Visual cue for disabled
        } else {
            String formattedPrice = "";
            if (productDetails.getSubscriptionOfferDetails() != null) {
                for (ProductDetails.SubscriptionOfferDetails offerDetails : productDetails.getSubscriptionOfferDetails()) {
                    // EXCLUSIVELY look for the monthly plan (P1M)
                    for (ProductDetails.PricingPhase phase : offerDetails.getPricingPhases().getPricingPhaseList()) {
                        if ("P1M".equals(phase.getBillingPeriod())) {
                            formattedPrice = phase.getFormattedPrice();
                            break;
                        }
                    }
                    if (!formattedPrice.isEmpty()) break;
                }
            }

            // If we still have no price, this product shouldn't be rendered
            if (!isOwned && formattedPrice.isEmpty()) {
                cardView.setVisibility(View.GONE);
                return;
            }

            priceView.setText(formattedPrice);            priceView.setTextColor(getResources().getColor(R.color.md_theme_primary, getTheme()));
            periodView.setVisibility(View.VISIBLE);
            card.setCheckable(true);
            card.setClickable(true);
            card.setAlpha(1.0f);

            cardView.setOnClickListener(v -> {
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
    }

    private void updateBuyButton() {
        MaterialButton buyAllButton = findViewById(R.id.buy_all_button);
        if (selectedProductIds.isEmpty()) {
            buyAllButton.setVisibility(View.GONE);
        } else {
            buyAllButton.setVisibility(View.VISIBLE);
            buyAllButton.setText("Subscribe Selected (" + selectedProductIds.size() + ")");
        }
    }

  @Override
  public void onBillingResponse(int responseCode, BillingResult billingResult) {
      showBillingResponseDialog(responseCode, billingResult);
      if (responseCode == BillingResponseCode.OK) {
          billingServiceClient.queryPurchases();
      }
  }

  @Override
  public void onProductDetailsFetched(Map<String, ProductDetails> productDetailsMap) {
      this.cachedProductDetailsMap = productDetailsMap;
      runOnUiThread(() -> billingServiceClient.queryPurchases());
  }

  @Override
  public void onPurchasesFetched(List<Purchase> purchases) {
      Log.i("TAG", "onPurchasesFetched: " + purchases);
      purchasedProductIds.clear();
      if (purchases != null) {
          for (Purchase purchase : purchases) {
              if (purchase.getPurchaseState() == Purchase.PurchaseState.PURCHASED) {
                  purchasedProductIds.addAll(purchase.getProducts());
              }
          }
      }
      runOnUiThread(() -> {
          if (cachedProductDetailsMap != null) {
              renderProductList(cachedProductDetailsMap);
          }
      });
  }

  private void renderProductList(Map<String, ProductDetails> productDetailsMap) {
      LinearLayout basePlansContainer = findViewById(R.id.base_plans_container);
      LinearLayout addOnsContainer = findViewById(R.id.add_ons_container);
      TextView basePlansHeader = findViewById(R.id.base_plans_header);
      TextView addOnsHeader = findViewById(R.id.add_ons_header);
      TextView noProductsText = findViewById(R.id.no_products_text);

      basePlansContainer.removeAllViews();
      addOnsContainer.removeAllViews();
      selectedProductIds.clear();
      updateBuyButton();

      if (productDetailsMap.isEmpty()) {
          noProductsText.setVisibility(View.VISIBLE);
          basePlansHeader.setVisibility(View.GONE);
          addOnsHeader.setVisibility(View.GONE);
      } else {
          noProductsText.setVisibility(View.GONE);
          LayoutInflater inflater = LayoutInflater.from(this);
          boolean hasBasePlans = false;
          boolean hasAddOns = false;

          for (ProductDetails productDetails : productDetailsMap.values()) {
              String productId = productDetails.getProductId();
              boolean isBasePlan = productId.startsWith("sidd607_subs");
              LinearLayout targetContainer = isBasePlan ? basePlansContainer : addOnsContainer;

              View cardView = inflater.inflate(R.layout.product_card, targetContainer, false);
              updateProductCardUI(cardView, productDetails);
              targetContainer.addView(cardView);

              if (isBasePlan) hasBasePlans = true;
              else hasAddOns = true;
          }

          basePlansHeader.setVisibility(hasBasePlans ? View.VISIBLE : View.GONE);
          addOnsHeader.setVisibility(hasAddOns ? View.VISIBLE : View.GONE);
      }
  }


  private int getDrawableProductImageForProductId(String productId) {
      return switch (productId) {
        case SUBS_PRODUCT_01 -> R.drawable.consumable_product_01;
        case SUBS_PRODUCT_02 -> R.drawable.consumable_product_02;
        case ADDON_SUBS_PRODUCT_01 -> R.drawable.consumable_product_03;
        case ADDON_SUBS_PRODUCT_02 -> R.drawable.consumable_product_04;
        case ADDON_SUBS_PRODUCT_03 -> R.drawable.consumable_product_05;
        default -> R.drawable.consumable_product_01;
      };
  }
}
