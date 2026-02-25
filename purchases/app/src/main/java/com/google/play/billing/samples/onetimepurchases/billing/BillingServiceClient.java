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
package com.google.play.billing.samples.onetimepurchases.billing;

import android.util.Log;
import androidx.appcompat.app.AppCompatActivity;
import com.android.billingclient.api.AcknowledgePurchaseParams;
import com.android.billingclient.api.AcknowledgePurchaseResponseListener;
import com.android.billingclient.api.BillingClient;
import com.android.billingclient.api.BillingClient.BillingResponseCode;
import com.android.billingclient.api.BillingClientStateListener;
import com.android.billingclient.api.BillingFlowParams;
import com.android.billingclient.api.BillingFlowParams.ProductDetailsParams;
import com.android.billingclient.api.BillingResult;
import com.android.billingclient.api.PendingPurchasesParams;
import com.android.billingclient.api.ProductDetails;
import com.android.billingclient.api.ProductDetailsResponseListener;
import com.android.billingclient.api.Purchase;
import com.android.billingclient.api.Purchase.PurchaseState;
import com.android.billingclient.api.PurchasesUpdatedListener;
import com.android.billingclient.api.QueryProductDetailsParams;
import com.android.billingclient.api.QueryProductDetailsParams.Product;
import com.android.billingclient.api.QueryProductDetailsResult;
import com.android.billingclient.api.QueryPurchasesParams;
import com.google.common.collect.ImmutableList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Manages interactions with the Google Play Billing Library for handling subscription bundles.
 */
public class BillingServiceClient {

  private static final String TAG = "Billing Service Client";
  private final BillingClient billingClient;
  private final AppCompatActivity activity;
  private BillingServiceClientListener billingServiceClientListener = null;
  private final Map<String, ProductDetails> productDetailsMap = new HashMap<>();

  private final AcknowledgePurchaseResponseListener acknowledgePurchaseResponseListener =
      billingResult -> Log.i(TAG, "Acknowledge purchase response: " + billingResult.getResponseCode());

  private final PurchasesUpdatedListener purchasesUpdatedListener =
      (billingResult, purchases) -> {
        int responseCode = billingResult.getResponseCode();
        billingServiceClientListener.onBillingResponse(responseCode, billingResult);

        if (responseCode == BillingResponseCode.OK && purchases != null) {
          for (Purchase purchase : purchases) {
            handlePurchase(purchase);
          }
        } else if (responseCode == BillingResponseCode.USER_CANCELED) {
          Log.e(TAG, "Purchase failed: User cancelled");
        } else {
          Log.e(TAG, "Purchase failed: " + billingResult.getDebugMessage());
        }
      };

  public BillingServiceClient(
      AppCompatActivity activity, BillingServiceClientListener billingServiceClientListener) {
    this.activity = activity;
    this.billingServiceClientListener = billingServiceClientListener;
    billingClient = createBillingClient();
  }

  public void startBillingConnection(ImmutableList<Product> productList) {
    Log.i(TAG, "Starting connection");
    billingClient.startConnection(
        new BillingClientStateListener() {
          @Override
          public void onBillingSetupFinished(BillingResult billingResult) {
            if (billingResult.getResponseCode() == BillingResponseCode.OK) {
              queryProductDetails(productList);
            } else {
              Log.e(TAG, "Billing connection failed: " + billingResult.getDebugMessage());
            }
          }

          @Override
          public void onBillingServiceDisconnected() {
            Log.e(TAG, "Billing Service connection lost.");
          }
        });
  }

  public void queryPurchases() {
    QueryPurchasesParams params = QueryPurchasesParams.newBuilder()
        .setProductType(BillingClient.ProductType.SUBS)
        .build();

    billingClient.queryPurchasesAsync(params, (billingResult, purchases) -> {
      if (billingResult.getResponseCode() == BillingResponseCode.OK) {
        billingServiceClientListener.onPurchasesFetched(purchases);
      } else {
        Log.e(TAG, "Query purchases failed: " + billingResult.getDebugMessage());
      }
    });
  }

  public ProductDetails getProductDetails(String productId) {
    return productDetailsMap.get(productId);
  }

  public void launchBillingFlow(String productId) {
    launchBillingFlow(ImmutableList.of(productId), null);
  }

  public void launchBillingFlow(List<String> productIds) {
    launchBillingFlow(productIds, null);
  }

  /**
   * Main method to launch the billing flow. Handles both new purchases and multi-line updates.
   */
  public void launchBillingFlow(List<String> productIds, String oldPurchaseToken) {
    Log.i(TAG, "Launching billing flow for: " + productIds + " (Update: " + (oldPurchaseToken != null) + ")");
    
    ImmutableList.Builder<ProductDetailsParams> productDetailsParamsListBuilder = ImmutableList.builder();

    for (String productId : productIds) {
      ProductDetails productDetails = productDetailsMap.get(productId);
      if (productDetails == null) {
        Log.e(TAG, "ProductDetails not found for: " + productId);
        continue;
      }

      ProductDetailsParams.Builder paramsBuilder = ProductDetailsParams.newBuilder()
          .setProductDetails(productDetails);

      if (productDetails.getProductType().equals(BillingClient.ProductType.SUBS)) {
        String offerToken = selectOfferToken(productDetails);
        if (offerToken != null) {
          paramsBuilder.setOfferToken(offerToken);
          Log.i(TAG, "Offer token selected for " + productId + ": " + offerToken);
        } else {
          Log.e(TAG, "CRITICAL: No offerToken found for subscription " + productId + ". This item will be skipped.");
          continue; // MUST have offerToken for subscriptions
        }
      }

      productDetailsParamsListBuilder.add(paramsBuilder.build());
    }

    ImmutableList<ProductDetailsParams> productDetailsParamsList = productDetailsParamsListBuilder.build();
    if (productDetailsParamsList.isEmpty()) {
      Log.e(TAG, "No valid products with offer tokens to purchase.");
      return;
    }

    BillingFlowParams.Builder billingFlowParamsBuilder = BillingFlowParams.newBuilder()
        .setProductDetailsParamsList(productDetailsParamsList);

    if (oldPurchaseToken != null && !oldPurchaseToken.isEmpty()) {
      Log.i(TAG, "Setting SubscriptionUpdateParams with token: " + oldPurchaseToken);
      billingFlowParamsBuilder.setSubscriptionUpdateParams(
          BillingFlowParams.SubscriptionUpdateParams.newBuilder()
              .setOldPurchaseToken(oldPurchaseToken)
              .build());
    }

    BillingResult result = billingClient.launchBillingFlow(activity, billingFlowParamsBuilder.build());
    if (result.getResponseCode() != BillingResponseCode.OK) {
      Log.e(TAG, "Launch billing flow failed: " + result.getResponseCode() + " - " + result.getDebugMessage());
    }
  }

  private String selectOfferToken(ProductDetails productDetails) {
    List<ProductDetails.SubscriptionOfferDetails> offers = productDetails.getSubscriptionOfferDetails();
    if (offers == null || offers.isEmpty()) {
        Log.e(TAG, "No subscription offers available for: " + productDetails.getProductId());
        return null;
    }

    Log.d(TAG, "Selecting offer for " + productDetails.getProductId() + ". Available offers: " + offers.size());
    
    // 1. Try to find a monthly plan (P1M) to match our UI preference
    for (ProductDetails.SubscriptionOfferDetails offer : offers) {
      for (ProductDetails.PricingPhase phase : offer.getPricingPhases().getPricingPhaseList()) {
        Log.d(TAG, "Checking phase: " + phase.getBillingPeriod() + " for offer: " + offer.getOfferId());
        if ("P1M".equals(phase.getBillingPeriod())) {
          return offer.getOfferToken();
        }
      }
    }

    // 2. Fallback: just return the first available offer token
    Log.w(TAG, "No P1M offer found for " + productDetails.getProductId() + ". Using fallback.");
    return offers.get(0).getOfferToken();
  }

  public void endBillingConnection() {
    billingClient.endConnection();
  }

  protected void setupProductDetailsMap(List<ProductDetails> productDetailsList) {
    if (productDetailsList != null) {
        for (ProductDetails details : productDetailsList) {
            if (details.getSubscriptionOfferDetails() != null) {
                boolean hasMonthly = false;
                for (ProductDetails.SubscriptionOfferDetails offer : details.getSubscriptionOfferDetails()) {
                    for (ProductDetails.PricingPhase phase : offer.getPricingPhases().getPricingPhaseList()) {
                        if ("P1M".equals(phase.getBillingPeriod())) {
                            hasMonthly = true;
                            break;
                        }
                    }
                    if (hasMonthly) break;
                }
                if (hasMonthly) {
                    productDetailsMap.put(details.getProductId(), details);
                } else {
                    Log.w(TAG, "Skipping product " + details.getProductId() + " - No P1M offer found.");
                }
            }
        }
    }
  }

  protected BillingClient createBillingClient() {
    return BillingClient.newBuilder(activity)
        .setListener(purchasesUpdatedListener)
        .enablePendingPurchases(PendingPurchasesParams.newBuilder()
            .enableOneTimeProducts()
            .build())
        .enableAutoServiceReconnection()
        .build();
  }

  private void handlePurchase(Purchase purchase) {
    if (purchase.getPurchaseState() == PurchaseState.PURCHASED && !purchase.isAcknowledged()) {
      AcknowledgePurchaseParams params = AcknowledgePurchaseParams.newBuilder()
          .setPurchaseToken(purchase.getPurchaseToken())
          .build();
      billingClient.acknowledgePurchase(params, acknowledgePurchaseResponseListener);
    }
  }

  private void queryProductDetails(ImmutableList<Product> productList) {
    QueryProductDetailsParams params = QueryProductDetailsParams.newBuilder().setProductList(productList).build();
    billingClient.queryProductDetailsAsync(params, (billingResult, result) -> {
      if (billingResult.getResponseCode() == BillingResponseCode.OK && result.getProductDetailsList() != null) {
        setupProductDetailsMap(result.getProductDetailsList());
        billingServiceClientListener.onProductDetailsFetched(productDetailsMap);
      } else {
        Log.e(TAG, "Query product details failed: " + billingResult.getDebugMessage());
      }
    });
  }
}