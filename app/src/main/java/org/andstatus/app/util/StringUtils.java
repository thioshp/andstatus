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

package org.andstatus.app.util;

import android.text.TextUtils;

/**
 * @author yvolk@yurivolkov.com
 */
public class StringUtils {

    public static String notEmpty(String value, String valueIfEmpty) {
        return TextUtils.isEmpty(value) ? valueIfEmpty : value;
    }

    public static String notNull(String value) {
        return value == null ? "" : value;
    }

    public static long toLong(String s) {
        long value = 0;
        try {
            value = Long.parseLong(s);
        } catch (NumberFormatException e) {
            MyLog.ignored(s, e);
        }
        return value;
    }

    /**
     * From http://stackoverflow.com/questions/767759/occurrences-of-substring-in-a-string
     */
    public static int countOfOccurrences(String str, String findStr) {
        int lastIndex = 0;
        int count = 0;
        while (lastIndex != -1) {
            lastIndex = str.indexOf(findStr, lastIndex);
            if (lastIndex != -1) {
                count++;
                lastIndex += findStr.length();
            }
        }
        return count;
    }
}
