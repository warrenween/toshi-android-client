/*
 * 	Copyright (c) 2017. Toshi Browser, Inc
 *
 * 	This program is free software: you can redistribute it and/or modify
 *     it under the terms of the GNU General Public License as published by
 *     the Free Software Foundation, either version 3 of the License, or
 *     (at your option) any later version.
 *
 *     This program is distributed in the hope that it will be useful,
 *     but WITHOUT ANY WARRANTY; without even the implied warranty of
 *     MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *     GNU General Public License for more details.
 *
 *     You should have received a copy of the GNU General Public License
 *     along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package com.toshi.model.network;

public class ReputationScore {
    private Double reputation_score;
    private Double average_rating;
    private int review_count;
    private Stars stars;

    public Double getReputationScore() {
        if (this.reputation_score == null) return 0.0;
        return reputation_score;
    }

    public Double getAverageRating() {
        if (this.average_rating == null) return 0.0;
        return average_rating;
    }

    public int getReviewCount() {
        return this.review_count;
    }

    public Stars getStars() {
        return this.stars;
    }
}
