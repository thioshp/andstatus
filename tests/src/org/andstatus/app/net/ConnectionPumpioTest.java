/* 
 * Copyright (c) 2013 yvolk (Yuri Volkov), http://yurivolkov.com
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

package org.andstatus.app.net;

import android.content.Context;
import android.test.InstrumentationTestCase;
import android.text.TextUtils;
import android.util.Log;

import org.andstatus.app.account.AccountDataReaderEmpty;
import org.andstatus.app.data.MyPreferences;
import org.andstatus.app.net.Connection.ApiRoutineEnum;
import org.andstatus.app.origin.Origin;
import org.andstatus.app.origin.OriginConnectionData;
import org.andstatus.app.origin.Origin.OriginEnum;
import org.andstatus.app.util.TriState;
import org.json.JSONObject;

import java.util.List;

public class ConnectionPumpioTest extends InstrumentationTestCase {
    private static final String TAG = ConnectionPumpioTest.class.getSimpleName();
    Context context;
    ConnectionPumpio connection;
    String host = "identi.ca";
    HttpConnectionMock httpConnection;
    OriginConnectionData connectionData;

    String keyStored;
    String secretStored;
    
    @Override
    protected void setUp() throws Exception {
        super.setUp();
        context = this.getInstrumentation().getTargetContext();
        if (context == null) {
            Log.e(TAG, "targetContext is null.");
            throw new IllegalArgumentException("this.getInstrumentation().getTargetContext() returned null");
        }
        MyPreferences.initialize(context, this);

        Origin origin = OriginEnum.PUMPIO.newOrigin();
        connectionData = origin.getConnectionData(TriState.UNKNOWN);
        connectionData.dataReader = new AccountDataReaderEmpty();
        connection = (ConnectionPumpio) connectionData.connectionClass.newInstance();
        connection.enrichConnectionData(connectionData);
        connectionData.httpConnectionClass = HttpConnectionMock.class;
        connection.setAccountData(connectionData);
        httpConnection = (HttpConnectionMock) connection.http;

        httpConnection.data.host = host;
        httpConnection.data.oauthClientKeys = OAuthClientKeys.fromConnectionData(httpConnection.data);
        keyStored = httpConnection.data.oauthClientKeys.getConsumerKey();
        secretStored = httpConnection.data.oauthClientKeys.getConsumerSecret();

        if (!httpConnection.data.oauthClientKeys.areKeysPresent()) {
            httpConnection.data.oauthClientKeys.setConsumerKeyAndSecret("keyForThetestGetTimeline", "thisIsASecret02341");
        }
    }
    
    @Override
    protected void tearDown() throws Exception {
        super.tearDown();
        if (!TextUtils.isEmpty(keyStored)) {
            httpConnection.data.oauthClientKeys.setConsumerKeyAndSecret(keyStored, secretStored);        
        }
    }

    public void testOidToObjectType() {
        String oids[] = {"https://identi.ca/api/activity/L4v5OL93RrabouQc9_QGfg", 
                "https://identi.ca/api/comment/ibpUqhU1TGCE2yHNbUv54g",
                "https://identi.ca/api/note/nlF5jl1HQciIs_zP85EeYg",
                "https://identi.ca/obj/ibpcomment",
                "http://identi.ca/notice/95772390",
                "acct:t131t@identi.ca",
                "http://identi.ca/user/46155"};
        String objectTypes[] = {"activity", 
                "comment", 
                "note",
                "unknown object type: https://identi.ca/obj/ibpcomment",
                "note",
                "person",
                "person"};
        for (int ind=0; ind < oids.length; ind++) {
            String oid = oids[ind];
            String objectType = objectTypes[ind];
            assertEquals("Expecting '" + objectType + "'", objectType, connection.oidToObjectType(oid));
        }
    }

    public void testUsernameToHost() {
        String usernames[] = {"t131t@identi.ca", 
                "somebody@example.com",
                "https://identi.ca/api/note/nlF5jl1HQciIs_zP85EeYg",
                "example.com",
                "@somewhere.com"};
        String hosts[] = {"identi.ca", 
                "example.com", 
                "",
                "",
                "somewhere.com"};
        for (int ind=0; ind < usernames.length; ind++) {
            assertEquals("Expecting '" + hosts[ind] + "'", hosts[ind], connection.usernameToHost(usernames[ind]));
        }
    }
    
    public void testGetTimeline() throws ConnectionException {
        String sinceId = "http://" + host + "/activity/frefq3232sf";

        JSONObject jso = RawResourceReader.getJSONObjectResource(this.getInstrumentation().getContext(), 
                org.andstatus.app.tests.R.raw.user_t131t_inbox);
        httpConnection.setResponse(jso);
        
        List<MbTimelineItem> timeline = connection.getTimeline(ApiRoutineEnum.STATUSES_HOME_TIMELINE, 
                new TimelinePosition(sinceId) , 20, "acct:t131t@" + host);
        assertNotNull("timeline returned", timeline);
        int size = 4;
        assertEquals("Response for t131t", size, timeline.size());

        int ind = 0;
        assertEquals("Posting image", MbTimelineItem.ItemType.MESSAGE, timeline.get(ind).getType());
        assertTrue("trailing linebreaks trimmed: '" + timeline.get(ind).mbMessage.body + "'", timeline.get(ind).mbMessage.body.endsWith("Link"));

        ind++;
        assertEquals("Other User", MbTimelineItem.ItemType.USER, timeline.get(ind).getType());
        assertEquals("Other actor", "acct:jpope@io.jpope.org", timeline.get(ind).mbUser.reader.oid);
        assertEquals("Following", TriState.TRUE, timeline.get(ind).mbUser.followedByReader);

        ind++;
        assertEquals("User", MbTimelineItem.ItemType.USER, timeline.get(ind).getType());
        MbUser mbUser = timeline.get(ind).mbUser;
        assertEquals("Following", TriState.TRUE, mbUser.followedByReader);

        ind++;
        assertTrue("Favorited by someone else", timeline.get(ind).mbMessage.favoritedByReader);
        assertEquals("Reader -someone else", "acct:jpope@io.jpope.org" , timeline.get(ind).mbMessage.reader.oid);
    }

    public void testGetUsersFollowedBy() throws ConnectionException {
        JSONObject jso = RawResourceReader.getJSONObjectResource(this.getInstrumentation().getContext(), 
                org.andstatus.app.tests.R.raw.user_t131t_following);
        httpConnection.setResponse(jso);
        
        assertTrue(connection.isApiSupported(ApiRoutineEnum.GET_FRIENDS));        
        assertTrue(connection.isApiSupported(ApiRoutineEnum.GET_FRIENDS_IDS));        
        
        List<MbUser> users = connection.getUsersFollowedBy("acct:t131t@" + host);
        assertNotNull("List of users returned", users);
        int size = 5;
        assertEquals("Response for t131t", size, users.size());

        assertEquals("Does the Pope shit in the woods?", users.get(1).description);
        assertEquals("gitorious@identi.ca", users.get(2).userName);
        assertEquals("acct:ken@coding.example", users.get(3).oid);
        assertEquals("Yuri Volkov", users.get(4).realName);
    }
}