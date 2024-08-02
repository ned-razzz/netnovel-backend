package com.ham.netnovel.coinCostPolicy.dto;

import com.ham.netnovel.coinCostPolicy.data.PolicyRange;
import jakarta.validation.constraints.NotNull;
import lombok.*;

@Getter
@Setter
@ToString
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CostPolicyDeleteDto {

    @NotNull
    private Long id;
}
