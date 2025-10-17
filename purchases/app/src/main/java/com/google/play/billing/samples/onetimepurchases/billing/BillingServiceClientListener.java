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

import com.android.billingclient.api.BillingResult;
import com.android.billingclient.api.ProductDetails;

import java.util.Map;

/** Interface for handling events from the BillingServiceClient class. */
public interface BillingServiceClientListener {
  /**
   * Called when a purchase is updated.
   *
   * @param billingResult The contains the message to display to the user.
   * @param responseCode The responseCode returned by the Billing API after billing flow
   */
  void onBillingResponse(int responseCode, BillingResult billingResult);
  void onProductDetailsFetched(Map<String, ProductDetails> productDetailsMap);
}
