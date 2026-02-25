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
import com.google.play.billing.samples.onetimepurchases.databinding.ActivityPurchasesBinding;
import com.google.play.billing.samples.onetimepurchases.databinding.ProductCardBinding;
import com.google.play.billing.samples.onetimepurchases.utils.BillingConstants;
import com.google.play.billing.samples.onetimepurchases.utils.ProductUIHelper;
import com.google.play.billing.samples.onetimepurchases.utils.LayoutUtils;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Activity to display and manage purchased subscriptions. */
public class PurchasesActivity extends AppCompatActivity implements BillingServiceClientListener {

  private BillingServiceClient billingServiceClient;
  private ActivityPurchasesBinding binding;
  private String activePurchaseToken = null;
  private final Set<String> ownedProductIds = new HashSet<>();
  private final Set<String> productsToAdd = new HashSet<>();
  private final Set<String> productsToRemove = new HashSet<>();
  private Map<String, ProductDetails> allProductDetails = new HashMap<>();

  @Override
  protected void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
    binding = ActivityPurchasesBinding.inflate(getLayoutInflater());
    setContentView(binding.getRoot());

    LayoutUtils.setupWindowInsets(binding.motionLayout);

    billingServiceClient = new BillingServiceClient(this, this);
    billingServiceClient.startBillingConnection(BillingConstants.PRODUCT_LIST);

    binding.updateAllButton.setOnClickListener(v -> {
        StringBuilder message = new StringBuilder("Summary of changes:\n");
        if (!productsToAdd.isEmpty()) {
            message.append("\nAdding:\n");
            for (String id : productsToAdd) {
                ProductDetails details = allProductDetails.get(id);
                if (details != null) message.append("• ").append(details.getName()).append("\n");
            }
        }
        if (!productsToRemove.isEmpty()) {
            message.append("\nRemoving:\n");
            for (String id : productsToRemove) {
                ProductDetails details = allProductDetails.get(id);
                if (details != null) message.append("• ").append(details.getName()).append("\n");
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
                        .setMessage("A subscription bundle must have at least one product.")
                        .setPositiveButton("OK", null).show();
                } else {
                    billingServiceClient.launchBillingFlow(finalProductList, activePurchaseToken);
                }
            })
            .setNegativeButton("Cancel", null).show();
    });
  }

  @Override
  public void onBillingResponse(int responseCode, BillingResult billingResult) {
      showBillingResponseDialog(responseCode, billingResult);
  }

  public void showBillingResponseDialog(int responseCode, BillingResult billingResult) {
    runOnUiThread(() -> {
          new MaterialAlertDialogBuilder(this)
              .setTitle(responseCode == BillingResponseCode.OK ? "Update Successful" : "Update Failed")
              .setMessage(billingResult.getDebugMessage())
              .setPositiveButton("OK", (dialog, which) -> {
                  if (responseCode == BillingResponseCode.OK) billingServiceClient.queryPurchases();
              }).show();
        });
  }

  @Override
  public void onProductDetailsFetched(Map<String, ProductDetails> productDetailsMap) {
      this.allProductDetails = productDetailsMap;
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
                  if (activePurchaseToken == null) activePurchaseToken = purchase.getPurchaseToken();
              }
          }
      }

      runOnUiThread(() -> {
          binding.purchasedProductsContainer.removeAllViews();
          productsToAdd.clear();
          productsToRemove.clear();

          if (ownedProductIds.isEmpty()) {
              binding.noSubscriptionsText.setVisibility(View.VISIBLE);
              binding.updateAllButton.setVisibility(View.GONE);
          } else {
              binding.noSubscriptionsText.setVisibility(View.GONE);
              binding.updateAllButton.setVisibility(View.VISIBLE);
              LayoutInflater inflater = getLayoutInflater();
              List<ProductDetails> sortedDetails = new ArrayList<>(allProductDetails.values());
              sortedDetails.sort((p1, p2) -> {
                  boolean p1Owned = ownedProductIds.contains(p1.getProductId());
                  boolean p2Owned = ownedProductIds.contains(p2.getProductId());
                  if (p1Owned && !p2Owned) return -1;
                  if (!p1Owned && p2Owned) return 1;
                  return p1.getName().compareTo(p2.getName());
              });

              for (ProductDetails details : sortedDetails) {
                  ProductCardBinding cardBinding = ProductCardBinding.inflate(inflater, binding.purchasedProductsContainer, false);
                  String productId = details.getProductId();
                  boolean isOwned = ownedProductIds.contains(productId);
                  boolean isSelected = isOwned ? productsToRemove.contains(productId) : productsToAdd.contains(productId);
                  
                  ProductUIHelper.bindProductCard(cardBinding, details, isOwned, isSelected);
                  
                  cardBinding.getRoot().setOnClickListener(v -> {
                      if (isOwned) {
                          if (productsToRemove.contains(productId)) productsToRemove.remove(productId);
                          else productsToRemove.add(productId);
                      } else {
                          if (productsToAdd.contains(productId)) productsToAdd.remove(productId);
                          else productsToAdd.add(productId);
                      }
                      
                      boolean nowSelected = isOwned ? productsToRemove.contains(productId) : productsToAdd.contains(productId);
                      ProductUIHelper.bindProductCard(cardBinding, details, isOwned, nowSelected);
                      updateUpdateButtonState();
                  });

                  binding.purchasedProductsContainer.addView(cardBinding.getRoot());
              }
              updateUpdateButtonState();
          }
      });
  }

  private void updateUpdateButtonState() {
      int totalChanges = productsToAdd.size() + productsToRemove.size();
      if (totalChanges == 0) {
          binding.updateAllButton.setText("No changes");
          binding.updateAllButton.setEnabled(false);
      } else {
          binding.updateAllButton.setText("Update Subscriptions (" + totalChanges + " changes)");
          binding.updateAllButton.setEnabled(true);
      }
  }

  @Override
  protected void onDestroy() {
    super.onDestroy();
    billingServiceClient.endBillingConnection();
    binding = null;
  }
}