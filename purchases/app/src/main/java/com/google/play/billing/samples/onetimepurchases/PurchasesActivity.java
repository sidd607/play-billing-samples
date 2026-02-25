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

import android.graphics.Paint;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.LinearLayout;
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
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

import android.content.res.ColorStateList;
import androidx.core.content.ContextCompat;

/** Activity to display and manage purchased subscriptions. */
public class PurchasesActivity extends AppCompatActivity implements BillingServiceClientListener {

  private BillingServiceClient billingServiceClient;
  private String activePurchaseToken = null;
  private final Set<String> ownedProductIds = new HashSet<>();
  private final Set<String> productsToAdd = new HashSet<>();
  private final Set<String> productsToRemove = new HashSet<>();
  private Map<String, ProductDetails> allProductDetails = new HashMap<>();
  
  private static final String SUBS_PRODUCT_01 = "sidd607_subs";
  private static final String SUBS_PRODUCT_02 = "sidd607_subs_premium";
  private static final String ADDON_SUBS_PRODUCT_01 = "kids_package";
  private static final String ADDON_SUBS_PRODUCT_02 = "news_package";
  private static final String ADDON_SUBS_PRODUCT_03 = "sports_package";

  private static final ImmutableList<Product> PRODUCT_LIST =
      ImmutableList.of(
          Product.newBuilder().setProductId(SUBS_PRODUCT_01).setProductType(ProductType.SUBS).build(),
          Product.newBuilder().setProductId(SUBS_PRODUCT_02).setProductType(ProductType.SUBS).build(),
          Product.newBuilder().setProductId(ADDON_SUBS_PRODUCT_01).setProductType(ProductType.SUBS).build(),
          Product.newBuilder().setProductId(ADDON_SUBS_PRODUCT_02).setProductType(ProductType.SUBS).build(),
          Product.newBuilder().setProductId(ADDON_SUBS_PRODUCT_03).setProductType(ProductType.SUBS).build());

  @Override
  protected void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
    setContentView(R.layout.activity_purchases);

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

    billingServiceClient = new BillingServiceClient(this, this);
    billingServiceClient.startBillingConnection(PRODUCT_LIST);

    MaterialButton updateButton = findViewById(R.id.update_all_button);
    updateButton.setOnClickListener(v -> {
        StringBuilder message = new StringBuilder("Summary of changes:\n");
        
        if (!productsToAdd.isEmpty()) {
            message.append("\nAdding:\n");
            for (String id : productsToAdd) {
                ProductDetails details = allProductDetails.get(id);
                if (details != null) {
                    message.append("• ").append(details.getName()).append("\n");
                }
            }
        }
        
        if (!productsToRemove.isEmpty()) {
            message.append("\nRemoving:\n");
            for (String id : productsToRemove) {
                ProductDetails details = allProductDetails.get(id);
                if (details != null) {
                    message.append("• ").append(details.getName()).append("\n");
                }
            }
        }
        
        new MaterialAlertDialogBuilder(this)
            .setTitle("Confirm Updates")
            .setMessage(message.toString())
            .setPositiveButton("Confirm", (dialog, which) -> {
                List<String> finalProductList = new ArrayList<>(ownedProductIds);
                finalProductList.removeAll(productsToRemove);
                finalProductList.addAll(productsToAdd);
                
                if (finalProductList.isEmpty()) {
                    new MaterialAlertDialogBuilder(this)
                        .setTitle("Cannot Empty Bundle")
                        .setMessage("A subscription bundle must have at least one product. Please keep at least one subscription.")
                        .setPositiveButton("OK", null)
                        .show();
                } else {
                    android.util.Log.i("PurchasesActivity", "Launching update for: " + finalProductList + " with token: " + activePurchaseToken);
                    billingServiceClient.launchBillingFlow(finalProductList, activePurchaseToken);
                }
            })
            .setNegativeButton("Cancel", null)
            .show();
    });
  }

  @Override
  public void onBillingResponse(int responseCode, BillingResult billingResult) {
      showBillingResponseDialog(responseCode, billingResult);
  }

  public void showBillingResponseDialog(int responseCode, BillingResult billingResult) {
    final String dialogTitle =
        responseCode == BillingResponseCode.OK ? "Update Successful" : "Update Failed";

    final String dialogMessage = billingResult.toString();

    runOnUiThread(
        () -> {
          new MaterialAlertDialogBuilder(this)
              .setTitle(dialogTitle)
              .setMessage(dialogMessage)
              .setPositiveButton("OK", (dialog, which) -> {
                  if (responseCode == BillingResponseCode.OK) {
                      billingServiceClient.queryPurchases();
                  }
              })
              .show();
        });
  }

  @Override
  public void onProductDetailsFetched(Map<String, ProductDetails> productDetailsMap) {
      this.allProductDetails = productDetailsMap;
      // After details are fetched, query actual purchases
      runOnUiThread(() -> billingServiceClient.queryPurchases());
  }

  @Override
  public void onPurchasesFetched(List<Purchase> purchases) {
      ownedProductIds.clear();
      activePurchaseToken = null;
      if (purchases != null) {
          for (Purchase purchase : purchases) {
              if (purchase.getPurchaseState() == Purchase.PurchaseState.PURCHASED) {
                  ownedProductIds.addAll(purchase.getProducts());
                  if (activePurchaseToken == null) {
                      activePurchaseToken = purchase.getPurchaseToken();
                  }
              }
          }
      }

      runOnUiThread(() -> {
          LinearLayout container = findViewById(R.id.purchased_products_container);
          TextView noSubsText = findViewById(R.id.no_subscriptions_text);
          MaterialButton updateButton = findViewById(R.id.update_all_button);
          
          container.removeAllViews();
          productsToAdd.clear();
          productsToRemove.clear();

          if (ownedProductIds.isEmpty()) {
              noSubsText.setVisibility(View.VISIBLE);
              updateButton.setVisibility(View.GONE);
          } else {
              noSubsText.setVisibility(View.GONE);
              updateButton.setVisibility(View.VISIBLE);
              LayoutInflater inflater = LayoutInflater.from(this);
              
              List<ProductDetails> sortedDetails = new ArrayList<>(allProductDetails.values());
              sortedDetails.sort((p1, p2) -> {
                  boolean p1Owned = ownedProductIds.contains(p1.getProductId());
                  boolean p2Owned = ownedProductIds.contains(p2.getProductId());
                  if (p1Owned && !p2Owned) return -1;
                  if (!p1Owned && p2Owned) return 1;
                  return p1.getName().compareTo(p2.getName());
              });

              for (ProductDetails details : sortedDetails) {
                  View cardView = inflater.inflate(R.layout.product_card, container, false);
                  updateProductCardUI(cardView, details);
                  container.addView(cardView);
              }
              updateUpdateButtonState();
          }
      });
  }

  private void updateProductCardUI(View cardView, ProductDetails productDetails) {
    String productId = productDetails.getProductId();
    boolean isOwned = ownedProductIds.contains(productId);
    
    TextView titleView = cardView.findViewById(R.id.product_title);
    TextView descView = cardView.findViewById(R.id.product_description);
    TextView priceView = cardView.findViewById(R.id.product_price);
    TextView periodView = cardView.findViewById(R.id.product_period);
    ShapeableImageView productImageView = cardView.findViewById(R.id.product_image);
    MaterialCardView card = (MaterialCardView) cardView;

    titleView.setText(productDetails.getName());
    descView.setText(productDetails.getDescription().strip().replace("\n", ""));
    productImageView.setImageResource(getDrawableProductImageForProductId(productId));

    if (isOwned) {
        priceView.setText("Subscribed");
        priceView.setTextColor(ContextCompat.getColor(this, android.R.color.holo_green_dark));
        periodView.setVisibility(View.GONE);
        
        card.setCheckedIconResource(R.drawable.ic_remove);
        card.setCheckedIconTint(ColorStateList.valueOf(ContextCompat.getColor(this, android.R.color.holo_red_dark)));
        
        boolean isRemoving = productsToRemove.contains(productId);
        card.setChecked(isRemoving);
        applyStrikethrough(titleView, descView, isRemoving);
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

        if (!ownedProductIds.contains(productId) && formattedPrice.isEmpty()) {
            cardView.setVisibility(View.GONE);
            return;
        }

        priceView.setText(formattedPrice);        priceView.setTextColor(ContextCompat.getColor(this, R.color.md_theme_primary));
        periodView.setVisibility(View.VISIBLE);

        card.setCheckedIconResource(R.drawable.ic_add);
        card.setCheckedIconTint(ColorStateList.valueOf(ContextCompat.getColor(this, android.R.color.holo_green_dark)));
        
        card.setChecked(productsToAdd.contains(productId));
    }

    cardView.setOnClickListener(v -> {
      if (isOwned) {
          if (productsToRemove.contains(productId)) {
              productsToRemove.remove(productId);
              card.setChecked(false);
              applyStrikethrough(titleView, descView, false);
          } else {
              productsToRemove.add(productId);
              card.setChecked(true);
              applyStrikethrough(titleView, descView, true);
          }
      } else {
          if (productsToAdd.contains(productId)) {
              productsToAdd.remove(productId);
              card.setChecked(false);
          } else {
              productsToAdd.add(productId);
              card.setChecked(true);
          }
      }
      updateUpdateButtonState();
    });
  }

  private void applyStrikethrough(TextView title, TextView desc, boolean apply) {
      if (apply) {
          title.setPaintFlags(title.getPaintFlags() | Paint.STRIKE_THRU_TEXT_FLAG);
          desc.setPaintFlags(desc.getPaintFlags() | Paint.STRIKE_THRU_TEXT_FLAG);
          title.setAlpha(0.5f);
          desc.setAlpha(0.5f);
      } else {
          title.setPaintFlags(title.getPaintFlags() & (~Paint.STRIKE_THRU_TEXT_FLAG));
          desc.setPaintFlags(desc.getPaintFlags() & (~Paint.STRIKE_THRU_TEXT_FLAG));
          title.setAlpha(1.0f);
          desc.setAlpha(1.0f);
      }
  }

  private void updateUpdateButtonState() {
      MaterialButton updateButton = findViewById(R.id.update_all_button);
      int totalChanges = productsToAdd.size() + productsToRemove.size();
      if (totalChanges == 0) {
          updateButton.setText("No changes");
          updateButton.setEnabled(false);
      } else {
          updateButton.setText("Update Subscriptions (" + totalChanges + " changes)");
          updateButton.setEnabled(true);
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

  @Override
  protected void onDestroy() {
    super.onDestroy();
    billingServiceClient.endBillingConnection();
  }
}