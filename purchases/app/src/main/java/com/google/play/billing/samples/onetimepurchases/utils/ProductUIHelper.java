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

import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.Paint;
import android.view.View;
import android.widget.TextView;
import androidx.core.content.ContextCompat;
import com.android.billingclient.api.ProductDetails;
import com.google.play.billing.samples.onetimepurchases.R;
import com.google.play.billing.samples.onetimepurchases.databinding.ProductCardBinding;
import static com.google.play.billing.samples.onetimepurchases.utils.BillingConstants.*;

public final class ProductUIHelper {
    private ProductUIHelper() {}

    public static int getProductImage(String productId) {
        return switch (productId) {
            case SUBS_PRODUCT_01 -> R.drawable.consumable_product_01;
            case SUBS_PRODUCT_02 -> R.drawable.consumable_product_02;
            case ADDON_SUBS_PRODUCT_01 -> R.drawable.consumable_product_03;
            case ADDON_SUBS_PRODUCT_02 -> R.drawable.consumable_product_05;
            case ADDON_SUBS_PRODUCT_03 -> R.drawable.consumable_product_04;
            default -> R.drawable.consumable_product_01;
        };
    }

    public static String getMonthlyPrice(ProductDetails productDetails) {
        if (productDetails.getSubscriptionOfferDetails() != null) {
            for (ProductDetails.SubscriptionOfferDetails offerDetails : productDetails.getSubscriptionOfferDetails()) {
                for (ProductDetails.PricingPhase phase : offerDetails.getPricingPhases().getPricingPhaseList()) {
                    if ("P1M".equals(phase.getBillingPeriod())) {
                        return phase.getFormattedPrice();
                    }
                }
            }
        }
        return "";
    }

    /**
     * Shared logic to bind ProductDetails to a product card view.
     */
    public static void bindProductCard(ProductCardBinding binding, 
                                     ProductDetails productDetails, 
                                     boolean isOwned, 
                                     boolean isSelected) {
        Context context = binding.getRoot().getContext();
        String productId = productDetails.getProductId();

        binding.productTitle.setText(productDetails.getName());
        binding.productDescription.setText(productDetails.getDescription().strip().replace("\n", ""));
        binding.productImage.setImageResource(getProductImage(productId));

        if (isOwned) {
            binding.productPrice.setText("Subscribed");
            binding.productPrice.setTextColor(ContextCompat.getColor(context, android.R.color.holo_green_dark));
            binding.productPeriod.setVisibility(View.GONE);
            
            // Selection logic for 'Removal' (Red X)
            binding.getRoot().setCheckedIconResource(R.drawable.ic_remove);
            binding.getRoot().setCheckedIconTint(ColorStateList.valueOf(ContextCompat.getColor(context, android.R.color.holo_red_dark)));
            binding.getRoot().setChecked(isSelected);
            
            applyStrikethrough(binding.productTitle, binding.productDescription, isSelected);
            
            if (isSelected) {
                binding.getRoot().setAlpha(0.6f);
            } else {
                binding.getRoot().setAlpha(1.0f);
            }
        } else {
            String formattedPrice = getMonthlyPrice(productDetails);
            binding.productPrice.setText(formattedPrice);
            binding.productPrice.setTextColor(ContextCompat.getColor(context, R.color.md_theme_primary));
            binding.productPeriod.setVisibility(View.VISIBLE);
            
            // Selection logic for 'Addition' (Green +)
            binding.getRoot().setCheckedIconResource(R.drawable.ic_add);
            binding.getRoot().setCheckedIconTint(ColorStateList.valueOf(ContextCompat.getColor(context, android.R.color.holo_green_dark)));
            binding.getRoot().setChecked(isSelected);
            
            applyStrikethrough(binding.productTitle, binding.productDescription, false);
            binding.getRoot().setAlpha(1.0f);
        }
    }

    public static void applyStrikethrough(TextView title, TextView desc, boolean apply) {
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
}