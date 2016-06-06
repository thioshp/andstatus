/*
 * Copyright (c) 2016 yvolk (Yuri Volkov), http://yurivolkov.com
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.andstatus.app.msg;

import android.content.ContentValues;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.text.TextUtils;

import org.andstatus.app.IntentExtra;
import org.andstatus.app.account.MyAccount;
import org.andstatus.app.context.MyContext;
import org.andstatus.app.context.MyContextHolder;
import org.andstatus.app.data.DbUtils;
import org.andstatus.app.data.ParsedUri;
import org.andstatus.app.data.TimelineType;
import org.andstatus.app.database.CommandTable;
import org.andstatus.app.database.TimelineTable;
import org.andstatus.app.origin.Origin;
import org.andstatus.app.util.BundleUtils;
import org.andstatus.app.util.ContentValuesUtils;
import org.andstatus.app.util.MyLog;

import java.util.ArrayList;
import java.util.List;

/**
 * @author yvolk@yurivolkov.com
 */
public class Timeline implements Comparable<Timeline> {
    private long id;
    private String name;
    private String description = "";

    private final TimelineType timelineType;
    private final MyAccount account;

    private boolean allOrigins = false;
    private Origin origin = Origin.Builder.buildUnknown();
    /**
     * Selected User for the {@link TimelineType#USER} timeline.
     * This is either User Id of current account OR user id of any other selected user.
     * So it's never == 0 for the {@link TimelineType#USER} timeline
     */
    private long userId = 0;
    /**
     * The string is not empty if this timeline is filtered using query string
     * ("Mentions" are not counted here because they have the separate TimelineType)
     */
    @NonNull
    private String searchQuery = "";

    private boolean synced = true;

    private boolean displayedInSelector = true;
    private long selectorOrder = 1;

    private long syncedDate = 0;
    private long syncFailedDate = 0;
    private String errorMessage = "";

    private long syncedTimesCount = 0;
    private long syncFailedTimesCount = 0;
    private long newItemsCount = 0;
    private long countSince = 0;

    private long syncedTimesCountTotal = 0;
    private long syncFailedTimesCountTotal = 0;
    private long newItemsCountTotal = 0;

    private String youngestPosition = "";
    private long youngestItemDate = 0;
    private long youngestSyncedDate = 0;

    private String oldestPosition = "";
    private long oldestItemDate = 0;
    private long oldestSyncedDate = 0;

    public Timeline(TimelineType timelineType, MyAccount myAccount) {
        this.timelineType = timelineType == null ? TimelineType.UNKNOWN : timelineType;
        this.account = myAccount == null ?  MyAccount.getEmpty() : myAccount;
    }

    @Override
    public int compareTo(Timeline another) {
        return selectorOrder == another.selectorOrder ? 0 :
                (selectorOrder >= another.selectorOrder ? 1 : -1 );
    }

    public void toContentValues(ContentValues values) {
        ContentValuesUtils.putNotZero(values, TimelineTable._ID, id);
        values.put(TimelineTable.TIMELINE_NAME, name);
        values.put(TimelineTable.TIMELINE_DESCRIPTION, description);
        values.put(TimelineTable.TIMELINE_TYPE, timelineType.save());
        values.put(TimelineTable.ALL_ORIGINS, allOrigins);
        if (origin.isValid()) {
            values.put(TimelineTable.ORIGIN_ID, origin.getId());
        }
        if (account.isValid()) {
            values.put(TimelineTable.ACCOUNT_ID, account.getUserId());
        }
        ContentValuesUtils.putNotZero(values, TimelineTable.USER_ID, userId);
        ContentValuesUtils.putNotEmpty(values, TimelineTable.SEARCH_QUERY, searchQuery);
        values.put(TimelineTable.SYNCED, synced);
        values.put(TimelineTable.DISPLAY_IN_SELECTOR, displayedInSelector);
        values.put(TimelineTable.SELECTOR_ORDER, selectorOrder);

        values.put(TimelineTable.SYNCED_DATE, syncedDate);
        values.put(TimelineTable.SYNC_FAILED_DATE, syncFailedDate);
        ContentValuesUtils.putNotEmpty(values, TimelineTable.ERROR_MESSAGE, errorMessage);

        values.put(TimelineTable.SYNCED_TIMES_COUNT, syncedTimesCount);
        values.put(TimelineTable.SYNC_FAILED_TIMES_COUNT, syncFailedTimesCount);
        values.put(TimelineTable.NEW_ITEMS_COUNT, newItemsCount);
        values.put(TimelineTable.COUNT_SINCE, countSince);
        values.put(TimelineTable.SYNCED_TIMES_COUNT_TOTAL, syncedTimesCountTotal);
        values.put(TimelineTable.SYNC_FAILED_TIMES_COUNT_TOTAL, syncFailedTimesCountTotal);
        values.put(TimelineTable.NEW_ITEMS_COUNT_TOTAL, newItemsCountTotal);

        ContentValuesUtils.putNotEmpty(values, TimelineTable.YOUNGEST_POSITION, youngestPosition);
        values.put(TimelineTable.YOUNGEST_ITEM_DATE, youngestItemDate);
        values.put(TimelineTable.YOUNGEST_SYNCED_DATE, youngestSyncedDate);
        ContentValuesUtils.putNotEmpty(values, TimelineTable.OLDEST_POSITION, oldestPosition);
        values.put(TimelineTable.OLDEST_ITEM_DATE, oldestItemDate);
        values.put(TimelineTable.OLDEST_SYNCED_DATE, oldestSyncedDate);
    }

    public void toCommandContentValues(ContentValues values) {
        ContentValuesUtils.putNotZero(values, CommandTable.TIMELINE_ID, id);
        values.put(CommandTable.TIMELINE_TYPE, timelineType.save());
        if (account.isValid()) {
            values.put(CommandTable.ACCOUNT_ID, account.getUserId());
        }
        ContentValuesUtils.putNotEmpty(values, CommandTable.SEARCH_QUERY, searchQuery);
        ContentValuesUtils.putNotZero(values, CommandTable.USER_ID, userId);
    }

    public static Timeline fromCursor(MyContext myContext, Cursor cursor) {
        TimelineType timelineType = TimelineType.load(DbUtils.getString(cursor, TimelineTable.TIMELINE_TYPE));
        MyAccount myAccount = myContext.persistentAccounts()
                .fromUserId(DbUtils.getLong(cursor, TimelineTable.ACCOUNT_ID));
        Timeline timeline = new Timeline(timelineType, myAccount);
        if (!TimelineType.UNKNOWN.equals(timelineType)) {
            timeline.id = DbUtils.getLong(cursor, TimelineTable._ID);

            timeline.name = DbUtils.getString(cursor, TimelineTable.TIMELINE_NAME);
            timeline.description = DbUtils.getString(cursor, TimelineTable.TIMELINE_DESCRIPTION);
            timeline.allOrigins = DbUtils.getBoolean(cursor, TimelineTable.ALL_ORIGINS);
            timeline.origin = myContext.persistentOrigins()
                    .fromId(DbUtils.getLong(cursor, TimelineTable.ORIGIN_ID));
            timeline.userId = DbUtils.getLong(cursor, TimelineTable.USER_ID);
            timeline.searchQuery = DbUtils.getString(cursor, TimelineTable.SEARCH_QUERY);
            timeline.synced = DbUtils.getBoolean(cursor, TimelineTable.SYNCED);
            timeline.displayedInSelector = DbUtils.getBoolean(cursor, TimelineTable.DISPLAY_IN_SELECTOR);
            timeline.selectorOrder = DbUtils.getLong(cursor, TimelineTable.SELECTOR_ORDER);

            timeline.syncedDate = DbUtils.getLong(cursor, TimelineTable.SYNCED_DATE);
            timeline.syncFailedDate = DbUtils.getLong(cursor, TimelineTable.SYNC_FAILED_DATE);
            timeline.errorMessage = DbUtils.getString(cursor, TimelineTable.ERROR_MESSAGE);

            timeline.syncedTimesCount = DbUtils.getLong(cursor, TimelineTable.SYNCED_TIMES_COUNT);
            timeline.syncFailedTimesCount = DbUtils.getLong(cursor, TimelineTable.SYNC_FAILED_TIMES_COUNT);
            timeline.newItemsCount = DbUtils.getLong(cursor, TimelineTable.NEW_ITEMS_COUNT);
            timeline.countSince = DbUtils.getLong(cursor, TimelineTable.COUNT_SINCE);
            timeline.syncedTimesCountTotal = DbUtils.getLong(cursor, TimelineTable.SYNCED_TIMES_COUNT_TOTAL);
            timeline.syncFailedTimesCountTotal = DbUtils.getLong(cursor, TimelineTable.SYNC_FAILED_TIMES_COUNT_TOTAL);
            timeline.newItemsCountTotal = DbUtils.getLong(cursor, TimelineTable.NEW_ITEMS_COUNT_TOTAL);

            timeline.youngestPosition = DbUtils.getString(cursor, TimelineTable.YOUNGEST_POSITION);
            timeline.youngestItemDate = DbUtils.getLong(cursor, TimelineTable.YOUNGEST_ITEM_DATE);
            timeline.youngestSyncedDate = DbUtils.getLong(cursor, TimelineTable.YOUNGEST_SYNCED_DATE);
            timeline.oldestPosition = DbUtils.getString(cursor, TimelineTable.OLDEST_POSITION);
            timeline.oldestItemDate = DbUtils.getLong(cursor, TimelineTable.OLDEST_ITEM_DATE);
            timeline.oldestSyncedDate = DbUtils.getLong(cursor, TimelineTable.OLDEST_SYNCED_DATE);
        }
        return timeline;
    }


    public static Timeline fromCommandCursor(MyContext myContext, Cursor cursor) {
        TimelineType timelineType = TimelineType.load(DbUtils.getString(cursor, CommandTable.TIMELINE_TYPE));
        MyAccount myAccount = myContext.persistentAccounts()
                .fromUserId(DbUtils.getLong(cursor, CommandTable.ACCOUNT_ID));
        Timeline timeline = new Timeline(timelineType, myAccount);
        timeline.id = DbUtils.getLong(cursor, CommandTable.TIMELINE_ID);
        timeline.searchQuery = DbUtils.getString(cursor, CommandTable.SEARCH_QUERY);
        timeline.userId = DbUtils.getLong(cursor, CommandTable.USER_ID);
        return timeline;
    }

    public static Timeline getEmpty(MyAccount myAccount) {
        return new Timeline(TimelineType.UNKNOWN, myAccount);
    }

    public boolean isEmpty() {
        return timelineType == TimelineType.UNKNOWN;
    }

    public boolean isValid() {
        return timelineType != TimelineType.UNKNOWN && id != 0;
    }

    public long getId() {
        return id;
    }

    public String getName() {
        if (TextUtils.isEmpty(name)) {
            return timelineType.getTitle(MyContextHolder.get().context()).toString();
        }
        return name;
    }

    public String getDescription() {
        return description;
    }

    public TimelineType getTimelineType() {
        return timelineType;
    }

    public boolean isAllOrigins() {
        return allOrigins;
    }

    public Origin getOrigin() {
        return origin;
    }

    public MyAccount getAccount() {
        return account;
    }

    public boolean isDisplayedInSelector() {
        return displayedInSelector;
    }

    public long getSelectorOrder() {
        return selectorOrder;
    }

    public static List<Timeline> addDefaultForAccount(MyAccount myAccount) {
        List<Timeline> timelines = new ArrayList<>();
        for (TimelineType timelineType : TimelineType.defaultTimelineTypes) {
            Timeline timeline = new Timeline(timelineType, myAccount);
            timeline.origin = myAccount.getOrigin();
            timeline.selectorOrder = timelineType.ordinal();
            timeline.synced = timelineType.isSyncableByDefault();
            timeline.save();
        }
        return timelines;
    }

    public long save() {
        ContentValues contentValues = new ContentValues();
        toContentValues(contentValues);
        if (getId() == 0) {
            id = DbUtils.addRowWithRetry(TimelineTable.TABLE_NAME, contentValues, 3);
        } else {
            DbUtils.updateRowWithRetry(TimelineTable.TABLE_NAME, getId(), contentValues, 3);
        }
        return getId();
    }

    public void delete() {
        SQLiteDatabase db = MyContextHolder.get().getDatabase();
        if (db == null) {
            MyLog.d(this, "delete; Database is unavailable");
        } else {
            String sql = "DELETE FROM " + TimelineTable.TABLE_NAME + " WHERE _ID=" + getId();
            db.execSQL(sql);
            MyLog.v(this, "Timeline deleted: " + this);
        }
    }

    @Override
    public String toString() {
        return "Timeline{" +
                account.getAccountName() +
                (TextUtils.isEmpty(name) ? "" : ", name:'" + name + '\'') +
                ", type:" + timelineType.save() +
                (userId == 0 ? "" : ", userId:" + userId) +
                '}';
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof Timeline)) return false;

        Timeline timeline = (Timeline) o;

        if (allOrigins != timeline.allOrigins) return false;
        if (userId != timeline.userId) return false;
        if (timelineType != timeline.timelineType) return false;
        if (!origin.equals(timeline.origin)) return false;
        if (!account.equals(timeline.account)) return false;
        return searchQuery.equals(timeline.searchQuery);
    }

    @Override
    public int hashCode() {
        int result = timelineType.hashCode();
        result = 31 * result + (allOrigins ? 1 : 0);
        result = 31 * result + origin.hashCode();
        result = 31 * result + account.hashCode();
        result = 31 * result + (int) (userId ^ (userId >>> 32));
        result = 31 * result + searchQuery.hashCode();
        return result;
    }

    public long getUserId() {
        return userId;
    }

    public static Timeline fromBundle(Bundle bundle) {
        MyAccount myAccount = MyAccount.fromBundle(bundle);
        Timeline timeline = getEmpty(myAccount);
        if (bundle != null) {
            timeline = MyContextHolder.get().persistentTimelines().fromId(
                    bundle.getLong(IntentExtra.TIMELINE_ID.key));
            if (timeline.isEmpty()) {
                Timeline timeline2 = new Timeline(
                        TimelineType.load(bundle.getString(IntentExtra.TIMELINE_TYPE.key)),
                        myAccount);
                timeline2.setSearchQuery(bundle.getString(IntentExtra.SEARCH_QUERY.key));
                timeline2.setUserId(bundle.getLong(IntentExtra.USER_ID.key));
                // TODO: set something else?!
                timeline = MyContextHolder.get().persistentTimelines().fromNewTimeLine(timeline2);
            }
        }
        return timeline;
    }

    public static Timeline fromParsedUri(MyContext myContext, ParsedUri parsedUri, String searchQuery) {
        Timeline timeline = new Timeline(
                parsedUri.getTimelineType(),
                myContext.persistentAccounts().fromUserId(parsedUri.getAccountUserId()));
        if (timeline.getTimelineType() == TimelineType.UNKNOWN ||
                parsedUri.getAccountUserId() == 0) {
            MyLog.e(Timeline.class,"fromParsedUri; uri:" + parsedUri.getUri()
                    + ", " + timeline.getTimelineType()
                    + ", accountId:" + parsedUri.getAccountUserId() );
            return timeline;
        }
        timeline.userId = parsedUri.getUserId();
        timeline.searchQuery = parsedUri.getSearchQuery();
        if (TextUtils.isEmpty(timeline.searchQuery) && !TextUtils.isEmpty(searchQuery)) {
            timeline.searchQuery = searchQuery;
        }
        return timeline;
    }

    @NonNull
    public String getSearchQuery() {
        return searchQuery;
    }

    public Timeline setSearchQuery(String searchQuery) {
        if (!TextUtils.isEmpty(searchQuery)) {
            this.searchQuery = searchQuery;
        }
        return this;
    }

    public void toBundle(Bundle bundle) {
        BundleUtils.putNotZero(bundle, IntentExtra.TIMELINE_ID, id);
        if (account.isValid()) {
            bundle.putString(IntentExtra.ACCOUNT_NAME.key, account.getAccountName());
        }
        if (timelineType != TimelineType.UNKNOWN) {
            bundle.putString(IntentExtra.TIMELINE_TYPE.key, timelineType.save());
        }
        BundleUtils.putNotEmpty(bundle, IntentExtra.SEARCH_QUERY, searchQuery);
        BundleUtils.putNotZero(bundle, IntentExtra.USER_ID, userId);
    }

    public Timeline setUserId(long userId) {
        this.userId = userId;
        return this;
    }
}
