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
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import com.android.billingclient.api.BillingClient.BillingResponseCode;
import com.android.billingclient.api.BillingClient.ProductType;
import com.android.billingclient.api.BillingFlowParams.ProductDetailsParams.SubscriptionProductReplacementParams.ReplacementMode;
import com.android.billingclient.api.BillingResult;
import com.android.billingclient.api.ProductDetails;
import com.android.billingclient.api.Purchase;
import com.android.billingclient.api.QueryProductDetailsParams.Product;
import com.google.android.material.bottomnavigation.BottomNavigationView;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.textfield.TextInputEditText;
import com.google.common.collect.ImmutableList;
import com.google.play.billing.samples.subscriptions.billing.BillingServiceClient;
import com.google.play.billing.samples.subscriptions.billing.BillingServiceClientListener;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class PlaygroundActivity extends AppCompatActivity implements BillingServiceClientListener {

  private static final String TAG = "PlaygroundActivity";
  private BillingServiceClient billingServiceClient;
  private String activePurchaseToken;
  private String activeProductId;
  private String selectedProductId;

  private final Map<String, ProductDetails> productDetailsMap = new HashMap<>();
  private final Map<String, String> customBasePlanMap = new HashMap<>();

  private static final String BASE_PLAN_MONTHLY = "monthly-auto-renewing";

  // Replacement modes to display
  private static final Map<String, Integer> REPLACEMENT_MODES =
      new HashMap<String, Integer>() {
        {
          put("CHARGE_FULL_PRICE", ReplacementMode.CHARGE_FULL_PRICE);
          put("CHARGE_PRORATED_PRICE", ReplacementMode.CHARGE_PRORATED_PRICE);
          put("WITH_TIME_PRORATION", ReplacementMode.WITH_TIME_PRORATION);
          put("WITHOUT_PRORATION", ReplacementMode.WITHOUT_PRORATION);
          put("DEFERRED", ReplacementMode.DEFERRED);
        }
      };

  private static final ImmutableList<Product> PRODUCT_LIST =
      ImmutableList.of(
          Product.newBuilder()
              .setProductId(MainActivity.SUBSCRIPTION_PRODUCT_01)
              .setProductType(ProductType.SUBS)
              .build(),
          Product.newBuilder()
              .setProductId(MainActivity.SUBSCRIPTION_PRODUCT_02)
              .setProductType(ProductType.SUBS)
              .build(),
          Product.newBuilder()
              .setProductId(MainActivity.SUBSCRIPTION_PRODUCT_03)
              .setProductType(ProductType.SUBS)
              .build());

  @Override
  protected void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
    setContentView(R.layout.activity_playground);

    setupNavigation();
    populateReplacementModes();

    // Setup Billing Client
    billingServiceClient = new BillingServiceClient(this, this);
    billingServiceClient.startBillingConnection(PRODUCT_LIST);

    findViewById(R.id.test_replacement_button).setOnClickListener(v -> testReplacement());
    findViewById(R.id.add_custom_sub_button).setOnClickListener(v -> showAddCustomSubDialog());
  }

  private void populateReplacementModes() {
    RadioGroup modeGroup = findViewById(R.id.replacement_mode_group);
    for (String modeName : REPLACEMENT_MODES.keySet()) {
      RadioButton rb = new RadioButton(this);
      rb.setText(modeName);
      rb.setId(View.generateViewId());
      rb.setTextColor(getColor(R.color.onSurface));
      rb.setPadding(32, 32, 32, 32);
      modeGroup.addView(rb);

      // Set default
      if (modeName.equals("CHARGE_PRORATED_PRICE")) {
        rb.setChecked(true);
      }
    }
  }

  private void showAddCustomSubDialog() {
    View view = getLayoutInflater().inflate(R.layout.dialog_add_custom_sub, null);
    TextInputEditText productIdEdit = view.findViewById(R.id.product_id_edit_text);
    TextInputEditText basePlanIdEdit = view.findViewById(R.id.base_plan_id_edit_text);

    new MaterialAlertDialogBuilder(this)
        .setTitle("Add Custom Subscription")
        .setView(view)
        .setPositiveButton(
            "Add",
            (dialog, which) -> {
              String productId = productIdEdit.getText().toString().trim();
              String basePlanId = basePlanIdEdit.getText().toString().trim();
              if (!productId.isEmpty()) {
                queryAndAddCustomSub(productId, basePlanId);
              }
            })
        .setNegativeButton("Cancel", null)
        .show();
  }

  private void queryAndAddCustomSub(@NonNull String productId, @NonNull String basePlanId) {
    ImmutableList<Product> products =
        ImmutableList.of(
            Product.newBuilder().setProductId(productId).setProductType(ProductType.SUBS).build());
    customBasePlanMap.put(productId, basePlanId);
    billingServiceClient.queryProductDetails(products);
  }

  private void testReplacement() {
    if (selectedProductId == null) {
      Log.w(TAG, "No target product selected");
      return;
    }
    ProductDetails productDetails = productDetailsMap.get(selectedProductId);

    if (productDetails == null) {
      Log.w(TAG, "No offers found for product: " + selectedProductId);
      return;
    }
    final String basePlanId = customBasePlanMap.getOrDefault(selectedProductId, "");
    List<ProductDetails.SubscriptionOfferDetails> offerDetailsList =
        productDetails.getSubscriptionOfferDetails();
    ProductDetails.SubscriptionOfferDetails offerDetail =
        offerDetailsList.stream()
            .filter(offer -> offer.getBasePlanId().equals(basePlanId))
            .findFirst()
            .orElse(offerDetailsList.get(0));

    String offerToken = offerDetail.getOfferToken();

    if (activeProductId != null && activePurchaseToken != null) {
      // Replacement mode
      RadioGroup modeGroup = findViewById(R.id.replacement_mode_group);
      int checkedId = modeGroup.getCheckedRadioButtonId();
      RadioButton checkedRb = findViewById(checkedId);

      if (checkedRb == null) {
        return;
      }

      Integer replacementMode = REPLACEMENT_MODES.get(checkedRb.getText().toString());
      if (replacementMode == null) {
        replacementMode = ReplacementMode.UNKNOWN_REPLACEMENT_MODE;
      }

      billingServiceClient.launchBillingFlow(
          selectedProductId, offerToken, activePurchaseToken, activeProductId, replacementMode);
    } else {
      // New purchase mode
      billingServiceClient.launchBillingFlow(selectedProductId, offerToken);
    }
  }

  private void setupNavigation() {
    BottomNavigationView bottomNav = findViewById(R.id.bottom_navigation);
    bottomNav.setSelectedItemId(R.id.navigation_playground);

    bottomNav.setOnItemSelectedListener(
        item -> {
          int itemId = item.getItemId();
          if (itemId == R.id.navigation_playground) {
            return true;
          } else if (itemId == R.id.navigation_home) {
            Intent intent = new Intent(this, MainActivity.class);
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
        .setSelectedItemId(R.id.navigation_playground);
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
    Log.i(TAG, "onBillingResponse: " + responseCode);
    if (responseCode == BillingResponseCode.OK) {
      billingServiceClient.queryPurchases();
    }
    final String dialogTitle =
        responseCode == BillingResponseCode.OK ? "Purchase Successful" : "Purchase Failed";
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
          refreshSubscriptionListUI();
          refreshActivePlanUI();
        });
  }

  private void refreshSubscriptionListUI() {
    LinearLayout container = findViewById(R.id.replacement_tiers_container);
    container.removeAllViews();

    for (ProductDetails details : productDetailsMap.values()) {
      addSubscriptionToUI(container, details);
    }
  }

  private void addSubscriptionToUI(LinearLayout container, ProductDetails details) {
    View card =
        getLayoutInflater().inflate(R.layout.playground_subscription_card, container, false);
    TextView productIdView = card.findViewById(R.id.subscription_product_id);
    TextView basePlanIdView = card.findViewById(R.id.subscription_base_plan);
    TextView billingPeriodView = card.findViewById(R.id.subscription_billing_period);
    TextView priceView = card.findViewById(R.id.subscription_price);

    String productId = details.getProductId();
    String basePlanId = customBasePlanMap.getOrDefault(productId, "");

    List<ProductDetails.SubscriptionOfferDetails> subscriptionOfferDetails =
        productDetailsMap.get(productId).getSubscriptionOfferDetails();
    ProductDetails.SubscriptionOfferDetails offerDetail =
        subscriptionOfferDetails.stream()
            .filter(offer -> offer.getBasePlanId().equals(basePlanId))
            .findFirst()
            .orElse(subscriptionOfferDetails.get(0));

    String price = offerDetail.getPricingPhases().getPricingPhaseList().get(0).getFormattedPrice();
    String billingCycle =
        offerDetail.getPricingPhases().getPricingPhaseList().get(0).getBillingPeriod();

    productIdView.setText(productId);
    basePlanIdView.setText(offerDetail.getBasePlanId());
    priceView.setText(price);
    billingPeriodView.setText(billingCycle);

    card.setOnClickListener(
        v -> {
          selectedProductId = details.getProductId();
          for (int i = 0; i < container.getChildCount(); i++) {
            container.getChildAt(i).setAlpha(0.6f);
            ((com.google.android.material.card.MaterialCardView) container.getChildAt(i))
                .setStrokeWidth(0);
          }
          card.setAlpha(1.0f);
          ((com.google.android.material.card.MaterialCardView) card).setStrokeWidth(4);
          ((com.google.android.material.card.MaterialCardView) card)
              .setStrokeColor(getColor(R.color.primary_red));
        });

    container.addView(card);
  }

  @Override
  public void onPurchasesFetched(List<Purchase> purchases) {
    runOnUiThread(
        () -> {
          if (purchases != null && !purchases.isEmpty()) {
            activePurchaseToken = purchases.get(0).getPurchaseToken();
            activeProductId = purchases.get(0).getProducts().get(0);
          } else {
            activePurchaseToken = null;
            activeProductId = null;
          }
          refreshActivePlanUI();
        });
  }

  private void refreshActivePlanUI() {
    TextView noActiveMsg = findViewById(R.id.no_active_purchase_msg);
    TextView subsHeader = findViewById(R.id.subs_section_header);
    TextView modeHeader = findViewById(R.id.replacement_mode_header);
    View modeContainer = findViewById(R.id.replacement_mode_container);
    MaterialButton actionBtn = findViewById(R.id.test_replacement_button);
    View activeCard = findViewById(R.id.active_subscription_card);

    if (activeProductId != null) {
      noActiveMsg.setVisibility(View.GONE);
      activeCard.setVisibility(View.VISIBLE);
      ((TextView) findViewById(R.id.active_subscription_name)).setText(activeProductId);
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

      subsHeader.setText(R.string.replacement_subs);
      modeHeader.setVisibility(View.VISIBLE);
      modeContainer.setVisibility(View.VISIBLE);
      actionBtn.setText(R.string.btn_text_test_replacement);
    } else {
      noActiveMsg.setVisibility(View.VISIBLE);
      activeCard.setVisibility(View.GONE);

      subsHeader.setText(R.string.text_subscriptions);
      modeHeader.setVisibility(View.GONE);
      modeContainer.setVisibility(View.GONE);
      actionBtn.setText(R.string.subscribe_btn_text);
    }
  }
}
