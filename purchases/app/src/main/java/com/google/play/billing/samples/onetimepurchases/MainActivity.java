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
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import androidx.appcompat.app.AppCompatActivity;
import com.android.billingclient.api.BillingClient.BillingResponseCode;
import com.android.billingclient.api.BillingResult;
import com.android.billingclient.api.ProductDetails;
import com.android.billingclient.api.Purchase;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.play.billing.samples.onetimepurchases.billing.BillingServiceClient;
import com.google.play.billing.samples.onetimepurchases.billing.BillingServiceClientListener;
import com.google.play.billing.samples.onetimepurchases.databinding.ActivityMainBinding;
import com.google.play.billing.samples.onetimepurchases.databinding.ProductCardBinding;
import com.google.play.billing.samples.onetimepurchases.utils.BillingConstants;
import com.google.play.billing.samples.onetimepurchases.utils.ProductUIHelper;
import com.google.play.billing.samples.onetimepurchases.utils.LayoutUtils;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** This is the main activity class */
public class MainActivity extends AppCompatActivity implements BillingServiceClientListener {

  private BillingServiceClient billingServiceClient;
  private ActivityMainBinding binding;
  private final Set<String> selectedProductIds = new HashSet<>();
  private final Set<String> purchasedProductIds = new HashSet<>();
  private Map<String, ProductDetails> cachedProductDetailsMap;

  @Override
  protected void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
    binding = ActivityMainBinding.inflate(getLayoutInflater());
    setContentView(binding.getRoot());

    LayoutUtils.setupWindowInsets(binding.motionLayout);

    billingServiceClient = new BillingServiceClient(this, this);
    billingServiceClient.startBillingConnection(BillingConstants.PRODUCT_LIST);

    binding.buyAllButton.setOnClickListener(v -> {
        if (!selectedProductIds.isEmpty()) {
            billingServiceClient.launchBillingFlow(new ArrayList<>(selectedProductIds));
        }
    });

    binding.viewPurchasesFab.setOnClickListener(v -> {
        Intent intent = new Intent(this, PurchasesActivity.class);
        startActivity(intent);
    });
  }

  public void showBillingResponseDialog(int responseCode, BillingResult billingResult) {
    final String dialogTitle = responseCode == BillingResponseCode.OK ? "Purchase Successful" : "Purchase Failed";
    runOnUiThread(() -> {
          new MaterialAlertDialogBuilder(this)
              .setTitle(dialogTitle)
              .setMessage(billingResult.getDebugMessage())
              .setPositiveButton("OK", (dialog, which) -> dialog.dismiss())
              .show();
        });
    }

  @Override
  protected void onDestroy() {
    super.onDestroy();
    billingServiceClient.endBillingConnection();
    binding = null;
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
      purchasedProductIds.clear();
      if (purchases != null) {
          for (Purchase purchase : purchases) {
              if (purchase.getPurchaseState() == Purchase.PurchaseState.PURCHASED) {
                  purchasedProductIds.addAll(purchase.getProducts());
              }
          }
      }
      runOnUiThread(() -> {
          if (cachedProductDetailsMap != null) renderProductList(cachedProductDetailsMap);
      });
  }

  private void renderProductList(Map<String, ProductDetails> productDetailsMap) {
      binding.basePlansContainer.removeAllViews();
      binding.addOnsContainer.removeAllViews();
      selectedProductIds.clear();
      updateBuyButton();

      if (productDetailsMap.isEmpty()) {
          binding.noProductsText.setVisibility(View.VISIBLE);
          binding.basePlansHeader.setVisibility(View.GONE);
          binding.addOnsHeader.setVisibility(View.GONE);
      } else {
          binding.noProductsText.setVisibility(View.GONE);
          LayoutInflater inflater = getLayoutInflater();
          boolean hasBasePlans = false;
          boolean hasAddOns = false;

          for (ProductDetails productDetails : productDetailsMap.values()) {
              String productId = productDetails.getProductId();
              boolean isOwned = purchasedProductIds.contains(productId);
              boolean isSelected = selectedProductIds.contains(productId);
              
              boolean isBasePlan = productId.startsWith(BillingConstants.SUBS_PRODUCT_01.substring(0, 7));
              
              ProductCardBinding cardBinding = ProductCardBinding.inflate(inflater, 
                  isBasePlan ? binding.basePlansContainer : binding.addOnsContainer, false);
              
              ProductUIHelper.bindProductCard(cardBinding, productDetails, isOwned, isSelected);
              
              if (!isOwned) {
                  cardBinding.getRoot().setOnClickListener(v -> {
                      boolean checked = !cardBinding.getRoot().isChecked();
                      cardBinding.getRoot().setChecked(checked);
                      if (checked) selectedProductIds.add(productId);
                      else selectedProductIds.remove(productId);
                      updateBuyButton();
                  });
              } else {
                  cardBinding.getRoot().setClickable(false);
                  cardBinding.getRoot().setCheckable(false);
              }

              if (isBasePlan) {
                  binding.basePlansContainer.addView(cardBinding.getRoot());
                  hasBasePlans = true;
              } else {
                  binding.addOnsContainer.addView(cardBinding.getRoot());
                  hasAddOns = true;
              }
          }

          binding.basePlansHeader.setVisibility(hasBasePlans ? View.VISIBLE : View.GONE);
          binding.addOnsHeader.setVisibility(hasAddOns ? View.VISIBLE : View.GONE);
      }
  }

  private void updateBuyButton() {
      if (selectedProductIds.isEmpty()) {
          binding.buyAllButton.setVisibility(View.GONE);
      } else {
          binding.buyAllButton.setVisibility(View.VISIBLE);
          binding.buyAllButton.setText("Buy Selected (" + selectedProductIds.size() + ")");
      }
  }
}