package com.buildvisionai.database;

import com.buildvisionai.database.entity.EstimateAddOnEntity;
import com.buildvisionai.database.entity.EstimateEntity;
import com.buildvisionai.database.entity.EstimateLineEntity;

import java.util.ArrayList;
import java.util.List;

/** One stored estimate with its lines and add-ons. A plain holder: no Room or Android types. */
public final class EstimateRows {
    public final EstimateEntity estimate;
    public final List<EstimateLineEntity> lines = new ArrayList<>();
    public final List<EstimateAddOnEntity> addOns = new ArrayList<>();

    public EstimateRows(EstimateEntity estimate) {
        this.estimate = estimate;
    }
}
