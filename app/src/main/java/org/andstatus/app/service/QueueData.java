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

package org.andstatus.app.service;

import android.support.annotation.NonNull;

import org.andstatus.app.context.MyContextHolder;

/**
 * @author yvolk@yurivolkov.com
 */
class QueueData implements Comparable<QueueData> {
    final QueueType queueType;
    final CommandData commandData;

    static QueueData getNew(QueueType queueType, CommandData commandData) {
        return new QueueData(queueType, commandData);
    }

    public QueueData(@NonNull QueueType queueType, @NonNull CommandData commandData) {
        this.queueType = queueType;
        this.commandData = commandData;
    }

    public long getId() {
        return commandData.hashCode();
    }

    public String toSharedSubject() {
        return queueType.getAcronym() + "; "
                + commandData.toCommandSummary(MyContextHolder.get());
    }

    public String toSharedText() {
        return queueType.getAcronym() + "; "
                + commandData.share(MyContextHolder.get());
    }

    @Override
    public int compareTo(QueueData another) {
        return -longCompare(commandData.getCreatedDate(), another.commandData.getCreatedDate());
    }

    // TODO: Replace with Long.compare for API >= 19
    public static int longCompare(long lhs, long rhs) {
        return lhs < rhs ? -1 : (lhs == rhs ? 0 : 1);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        QueueData queueData = (QueueData) o;
        if (queueType != queueData.queueType) return false;
        return commandData.getCreatedDate() == queueData.commandData.getCreatedDate();
    }

    @Override
    public int hashCode() {
        int result = queueType.hashCode();
        result = 31 * result + (int) (commandData.getCreatedDate() ^ (commandData.getCreatedDate() >>> 32));
        return result;
    }

    public static QueueData getEmpty() {
        return new QueueData(QueueType.UNKNOWN, CommandData.getEmpty());
    }
}
