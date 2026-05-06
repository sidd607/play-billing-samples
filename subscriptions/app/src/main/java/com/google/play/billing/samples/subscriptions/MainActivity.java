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
package com.google.play.billing.samples.subscriptions;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;
import androidx.appcompat.app.AppCompatActivity;
import com.android.billingclient.api.BillingClient;
import com.android.billingclient.api.BillingClient.ProductType;
import com.android.billingclient.api.BillingFlowParams.ProductDetailsParams.SubscriptionProductReplacementParams;
import com.android.billingclient.api.BillingResult;
import com.android.billingclient.api.ProductDetails;
import com.android.billingclient.api.Purchase;
import com.android.billingclient.api.QueryProductDetailsParams.Product;
import com.google.android.material.bottomnavigation.BottomNavigationView;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.common.collect.ImmutableList;
import com.google.play.billing.samples.subscriptions.billing.BillingServiceClient;
import com.google.play.billing.samples.subscriptions.billing.BillingServiceClientListener;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class MainActivity extends AppCompatActivity implements BillingServiceClientListener {

  private static final String TAG = "MainActivity";
  private BillingServiceClient billingServiceClient;

  public static final String SUBSCRIPTION_PRODUCT_01 = "subscription_premium";
  public static final String SUBSCRIPTION_PRODUCT_02 = "subscription_basic";
  public static final String SUBSCRIPTION_PRODUCT_03 = "subscription_lite";
  private static final String BASE_PLAN_MONTHLY = "monthly-auto-renewing";

  private static final ImmutableList<Product> PRODUCT_LIST =
      ImmutableList.of(
          Product.newBuilder()
              .setProductId(SUBSCRIPTION_PRODUCT_01)
              .setProductType(ProductType.SUBS)
              .build(),
          Product.newBuilder()
              .setProductId(SUBSCRIPTION_PRODUCT_02)
              .setProductType(ProductType.SUBS)
              .build(),
          Product.newBuilder()
              .setProductId(SUBSCRIPTION_PRODUCT_03)
              .setProductType(ProductType.SUBS)
              .build());

  private final Map<String, ProductDetails> productDetailsMap = new HashMap<>();
  private String activeProductId = null;
  private String activePurchaseToken = null;

  @Override
  protected void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
    setContentView(R.layout.activity_main);

    setupNavigation();

    // Setup Billing Client
    billingServiceClient = new BillingServiceClient(this, this);
    billingServiceClient.startBillingConnection(PRODUCT_LIST);
  }

  private void setupNavigation() {
    BottomNavigationView bottomNav = findViewById(R.id.bottom_navigation);
    bottomNav.setSelectedItemId(R.id.navigation_home);

    bottomNav.setOnItemSelectedListener(
        item -> {
          int itemId = item.getItemId();
          if (itemId == R.id.navigation_home) {
            return true;
          } else if (itemId == R.id.navigation_playground) {
            Intent intent = new Intent(this, PlaygroundActivity.class);
            intent.setFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT);
            startActivity(intent);
            overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out);
            return true;
          }
          return false;
        });
  }

  @Override
  protected void onResume() {
    super.onResume();
    ((BottomNavigationView) findViewById(R.id.bottom_navigation))
        .setSelectedItemId(R.id.navigation_home);
    if (billingServiceClient != null) {
      billingServiceClient.queryPurchases();
    }
  }

  @Override
  protected void onDestroy() {
    super.onDestroy();
    billingServiceClient.endBillingConnection();
  }

  @Override
  public void onBillingResponse(int responseCode, BillingResult billingResult) {
    Log.i(TAG, "onBillingResponse: " + responseCode + " " + billingResult.getDebugMessage());
    final String dialogTitle =
        responseCode == BillingClient.BillingResponseCode.OK
            ? "Purchase Successful"
            : "Purchase Failed";
    final String dialogMessage = billingResult.toString();

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
  public void onProductDetailsFetched(Map<String, ProductDetails> productDetailsMap) {
    runOnUiThread(
        () -> {
          this.productDetailsMap.putAll(productDetailsMap);
          refreshExplorePlans();
        });
  }

  @Override
  public void onPurchasesFetched(List<Purchase> purchases) {
    runOnUiThread(
        () -> {
          if (purchases != null && !purchases.isEmpty()) {
            activeProductId = purchases.get(0).getProducts().get(0);
            activePurchaseToken = purchases.get(0).getPurchaseToken();
          } else {
            activeProductId = null;
            activePurchaseToken = null;
          }
          refreshActivePlanUI();
          refreshExplorePlans();
        });
  }

  private void refreshActivePlanUI() {
    if (activeProductId != null && !activeProductId.isEmpty()) {
      findViewById(R.id.active_subscription_card).setVisibility(View.VISIBLE);
      String displayName = activeProductId;
      ProductDetails details = productDetailsMap.get(activeProductId);
      if (details != null) {
        displayName = details.getName();
      }

      ((TextView) findViewById(R.id.active_subscription_name)).setText(displayName);

      findViewById(R.id.manage_plan_button)
          .setOnClickListener(
              v -> {
                String url =
                    String.format(
                        "https://play.google.com/store/account/subscriptions?sku=%s&package=%s",
                        activeProductId, getPackageName());
                Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(url));
                startActivity(intent);
              });
    } else {
      findViewById(R.id.active_subscription_card).setVisibility(View.GONE);
    }
  }

  private void refreshExplorePlans() {
    LinearLayout container = findViewById(R.id.plans_container);
    container.removeAllViews();

    for (ProductDetails details : productDetailsMap.values()) {
      if (activeProductId != null && activeProductId.equals(details.getProductId())) {
        continue;
      }
      addPlanToUI(container, details);
    }
  }

  private void addPlanToUI(LinearLayout container, ProductDetails details) {
    View card = getLayoutInflater().inflate(R.layout.subscription_card_item, container, false);

    TextView titleView = card.findViewById(R.id.product_title);
    TextView priceView = card.findViewById(R.id.product_price);
    MaterialButton actionButton = card.findViewById(R.id.switch_button);

    titleView.setText(details.getName());

    List<ProductDetails.SubscriptionOfferDetails> offers = details.getSubscriptionOfferDetails();
    if (offers != null && !offers.isEmpty()) {
      ProductDetails.SubscriptionOfferDetails selectedOffer =
          offers.stream()
              .filter(offer -> BASE_PLAN_MONTHLY.equals(offer.getBasePlanId()))
              .findFirst()
              .orElse(offers.get(0));
      ;
      String price =
          selectedOffer.getPricingPhases().getPricingPhaseList().get(0).getFormattedPrice();
      priceView.setText(String.format("%s/mo", price));

      if (activeProductId != null && !activeProductId.isEmpty()) {
        // Case where the user is trying to switch their subscription plan
        actionButton.setText(R.string.switch_button_text);

        int replacementMode =
            SubscriptionProductReplacementParams.ReplacementMode.CHARGE_PRORATED_PRICE;

        actionButton.setOnClickListener(
            v ->
                billingServiceClient.launchBillingFlow(
                    details.getProductId(),
                    selectedOffer.getOfferToken(),
                    activePurchaseToken,
                    activeProductId,
                    replacementMode));

      } else {
        // There exist no active subscription for the user, treating this as a new purchase.
        actionButton.setText(R.string.subscribe_btn_text);
        actionButton.setOnClickListener(
            v ->
                billingServiceClient.launchBillingFlow(
                    details.getProductId(), selectedOffer.getOfferToken()));
      }
    }

    container.addView(card);
  }
}
