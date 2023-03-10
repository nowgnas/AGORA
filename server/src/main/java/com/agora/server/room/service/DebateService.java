package com.agora.server.room.service;

import com.agora.server.debatehistory.domain.DebateHistory;
import com.agora.server.debatehistory.service.DebateHistoryService;
import com.agora.server.file.dto.FileDto;
import com.agora.server.file.service.FileService;
import com.agora.server.room.controller.dto.RequestDebateStartDto;
import com.agora.server.room.controller.dto.RequestRoomEnterAsDebaterDto;
import com.agora.server.room.controller.dto.RequestRoomLeaveDto;
import com.agora.server.room.controller.dto.debate.RequestReadyStateChangeDto;
import com.agora.server.room.controller.dto.debate.RequestSkipDto;
import com.agora.server.room.controller.dto.debate.RequestVoteDto;
import com.agora.server.room.domain.Room;
import com.agora.server.room.exception.NotReadyException;
import com.agora.server.room.repository.RoomRepository;
import com.agora.server.room.util.RedisChannelUtil;
import com.agora.server.room.util.RedisKeyUtil;
import com.agora.server.room.util.RedisMessageUtil;
import com.agora.server.sse.service.PublishService;
import com.agora.server.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.ListOperations;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

@Service
@RequiredArgsConstructor
public class DebateService {


    private final RedisPublisher redisPublisher;

    private final RoomRepository roomRepository;
    private final FileService fileService;

    private final RedisTemplate<String, Object> redisTemplate;

    private final Map<String, ScheduledFuture<?>> scheduledFutures;

    private final RedisKeyUtil redisKeyUtil;

    private final RedisChannelUtil redisChannelUtil;

    private final RedisMessageUtil redisMessageUtil;

    private final DebateHistoryService debateHistoryService;

    private final Map<String, List<SseEmitter>> roomEmitterMap;

    private final PublishService publishService;


    /**
     * ????????? ?????? Redis Pub/Sub
     * <p>
     * ???????????????????????? Redis??? ?????? ?????????
     * ??????????????? ????????? SUBSCRIBE room:roomId room:roomId:debater room:roomId:left
     * ?????????????????? ????????? SUBSCRIBE room:roomId room:roomId:debater room:roomId:right
     * (????????? ?????? room:roomId, room:roomId:debate, room:roomId:left ??? ?????? ????????? ???????????? ??????????????????)
     * <p>
     * ???????????? Redis??? ?????? ?????????
     * room:roomId ????????? ????????? ?????? ?????? ????????? ????????? ??????
     * PUBLISH room:roomId [ENTER][LEFT] ??????????????????
     * -> ?????? ?????? ????????? ??????
     * PUBLISH room:roomId [ENTER][RIGHT] ??????????????????
     * -> ????????? ?????? ????????? ??????
     * ???????????????????????? ?????? ????????? ??????, ????????? ??? ?????????(?????????)?????? ????????? ????????????
     * ??? ???????????? ????????? ???????????? ????????? ???????????? ????????? ???????????? ?????? ????????? ?????????.
     */
    public void debaterEnter(RequestRoomEnterAsDebaterDto requestRoomEnterDto) {
        Long roomId = requestRoomEnterDto.getRoomId();
//        String userNickname = requestRoomEnterDto.getUserNickname();
//        String userTeam = requestRoomEnterDto.getUserTeam();

        String leftUserListKey = redisKeyUtil.leftUserListKey(roomId);
        String rightUserListKey = redisKeyUtil.rightUserListKey(roomId);
        List<Object> oleftUserList = redisTemplate.opsForList().range(leftUserListKey, 0, -1);
        List<Object> orightUserList = redisTemplate.opsForList().range(rightUserListKey, 0, -1);
        List<String> leftUserList = new ArrayList<>();
        List<String> rightUserList = new ArrayList<>();
        for (Object o : oleftUserList) {
            leftUserList.add((String) o);
        }
        for (Object o : orightUserList) {
            rightUserList.add((String) o);
        }
        String roomChannel = redisChannelUtil.roomChannelKey(roomId);
        String enterMessage = redisMessageUtil.enterMessage(leftUserList, rightUserList);

        redisPublisher.publishMessage(roomChannel, enterMessage);
    }

    /**
     * ????????? ?????? Redis Pub/Sub
     * <p>
     * ???????????????????????? Redis??? ?????? ?????????
     * ????????? UNSUBSCRIBE
     * (UNSUBSCRIBE??? ?????? ?????? ???????????? ?????? ????????? Redis ??????????????????)
     * <p>
     * ???????????? Redis??? ?????? ?????????
     * room:roomId ????????? ????????? ?????? ?????? ????????? ????????? ??????
     * PUBLISH room:roomId [LEAVE][LEFT] ??????????????????
     * -> ?????? ????????? ????????? ??????
     * PUBLISH room:roomId [LEAVE][RIGHT] ??????????????????
     * -> ????????? ????????? ????????? ??????
     * ???????????????????????? ?????? ????????? ??????, ????????? ??? ?????????(?????????)?????? ????????? ????????????
     * ??? ???????????? ????????? ???????????? ????????? ???????????? ????????? ??????????????? ?????? ????????? ?????????.
     */
    public void debaterLeave(RequestRoomLeaveDto requestRoomLeaveDto) {

        Long roomId = requestRoomLeaveDto.getRoomId();

        String leftUserListKey = redisKeyUtil.leftUserListKey(roomId);
        String rightUserListKey = redisKeyUtil.rightUserListKey(roomId);
        List<Object> oleftUserList = redisTemplate.opsForList().range(leftUserListKey, 0, -1);
        List<Object> orightUserList = redisTemplate.opsForList().range(rightUserListKey, 0, -1);
        List<String> leftUserList = new ArrayList<>();
        List<String> rightUserList = new ArrayList<>();
        for (Object o : oleftUserList) {
            leftUserList.add((String) o);
        }
        for (Object o : orightUserList) {
            rightUserList.add((String) o);
        }

        String roomChannel = redisChannelUtil.roomChannelKey(roomId);
        String leaveMessage = redisMessageUtil.leaveMessage(leftUserList, rightUserList);

        redisPublisher.publishMessage(roomChannel, leaveMessage);
    }


    public void ready(RequestReadyStateChangeDto requestReadyStateChangeDto) {

        Long roomId = requestReadyStateChangeDto.getRoomId();
        String readyUserNickname = requestReadyStateChangeDto.getUserNickname();

        /**
         * Redis?????? ???????????? TRUE??? ??????
         */
        String isReadyKey = redisKeyUtil.isReadyKey(roomId, readyUserNickname);
        redisTemplate.opsForValue().set(isReadyKey, "TRUE");

        /**
         * PubSub?????? ????????? ?????? ????????????
         */
        ArrayList<String> readyUserList = new ArrayList<>();

        String leftUserListKey = redisKeyUtil.leftUserListKey(roomId);
        ArrayList<String> leftUserList = new ArrayList<>();

        if (redisTemplate.type(leftUserListKey) != null) {
            List<Object> range = redisTemplate.opsForList().range(leftUserListKey, 0, -1);
            for (Object o : range) {
                String userNickname = (String) o;
                leftUserList.add(userNickname);
                Object o1 = redisTemplate.opsForValue().get(redisKeyUtil.isReadyKey(roomId, userNickname));
                String isReady = (String) o1;
                if (isReady.equals("TRUE")) {
                    readyUserList.add(userNickname);
                }
            }
        }

        String rightUserListKey = redisKeyUtil.rightUserListKey(roomId);
        ArrayList<String> rightuserls = new ArrayList<>();
        if (redisTemplate.type(rightUserListKey) != null) {
            List<Object> range = redisTemplate.opsForList().range(rightUserListKey, 0, -1);
            for (Object o : range) {
                String userNickname = (String) o;
                rightuserls.add(userNickname);
                Object o1 = redisTemplate.opsForValue().get(redisKeyUtil.isReadyKey(roomId, userNickname));
                String isReady = (String) o1;
                if (isReady.equals("TRUE")) {
                    readyUserList.add(userNickname);
                }
            }
        }

        Boolean isAllReady = false;
        if (readyUserList.size() == 5) {
            isAllReady = true;
        }

        String roomChannel = redisChannelUtil.roomChannelKey(roomId);
        String readyMessage = redisMessageUtil.readyMessage(isAllReady, readyUserList);
        redisPublisher.publishMessage(roomChannel, readyMessage);

    }


    public void unready(RequestReadyStateChangeDto requestReadyStateChangeDto) {

        Long roomId = requestReadyStateChangeDto.getRoomId();
        String unreadyUserNickname = requestReadyStateChangeDto.getUserNickname();

        /**
         * Redis?????? ???????????? FALSE??? ??????
         */
        String isReadyKey = redisKeyUtil.isReadyKey(roomId, unreadyUserNickname);
        redisTemplate.opsForValue().set(isReadyKey, "FALSE");

        /**
         * PubSub?????? ????????? ?????? ????????????
         */
        ArrayList<String> readyUserList = new ArrayList<>();

        String leftUserListKey = redisKeyUtil.leftUserListKey(roomId);
        ArrayList<String> leftUserList = new ArrayList<>();

        if (redisTemplate.type(leftUserListKey) != null) {
            List<Object> range = redisTemplate.opsForList().range(leftUserListKey, 0, -1);
            for (Object o : range) {
                String userNickname = (String) o;
                leftUserList.add(userNickname);
                Object o1 = redisTemplate.opsForValue().get(redisKeyUtil.isReadyKey(roomId, userNickname));
                String isReady = (String) o1;
                if (isReady.equals("TRUE")) {
                    readyUserList.add(userNickname);
                }
            }
        }

        String rightUserListKey = redisKeyUtil.rightUserListKey(roomId);
        ArrayList<String> rightuserls = new ArrayList<>();
        if (redisTemplate.type(rightUserListKey) != null) {
            List<Object> range = redisTemplate.opsForList().range(rightUserListKey, 0, -1);
            for (Object o : range) {
                String userNickname = (String) o;
                rightuserls.add(userNickname);
                Object o1 = redisTemplate.opsForValue().get(redisKeyUtil.isReadyKey(roomId, userNickname));
                String isReady = (String) o1;
                if (isReady.equals("TRUE")) {
                    readyUserList.add(userNickname);
                }
            }
        }

        Boolean isAllReady = false;
        if (readyUserList.size() == 6) {
            isAllReady = true;
        }

        String roomChannel = redisChannelUtil.roomChannelKey(roomId);
        String unreadyMessage = redisMessageUtil.unreadyMessage(isAllReady, readyUserList);
        redisPublisher.publishMessage(roomChannel, unreadyMessage);

    }

    /**
     * ????????? ?????? ?????? ?????? Redis Pub/Sub
     * ??? ?????? ??? ?????? ??????
     *
     * ???????????????????????? Redis??? ?????? ?????????
     *
     * ???????????? Redis??? ?????? ?????????
     */

    /**
     * ????????? ???,?????? Redis Pub/Sub
     *
     * ???????????????????????? Redis??? ?????? ?????????
     * ????????? SUBSCRIBE room:roomId room:roomId:watcher
     * ????????? UNSUBSCRIBE
     * ??? ???????????? redis ????????? ??????
     *
     * ???????????? Redis??? ?????? ?????????
     * ????????????
     * ??? ????????? ????????? ????????? ??? ???????????? ????????? ????????? ?????? ??? ?????? ????????? ?????? ???????????? ????????????
     */


    /**
     * ?????? ?????? Redis Pub/Sub
     * <p>
     * ???????????????????????? Redis??? ?????? ?????????
     * ????????????
     * <p>
     * ???????????? Redis??? ?????? ?????????
     * PUBLISH room:roomId [START] start debate
     * ?????? ?????????????????? isReady ????????? ?????? ??????
     */
    public void startDebate(RequestDebateStartDto requestDebateStartDto) throws NotReadyException {

        Long roomId = requestDebateStartDto.getRoomId();

        String leftUserListKey = redisKeyUtil.leftUserListKey(roomId);
        String rightUserListKey = redisKeyUtil.rightUserListKey(roomId);
        List<Object> oleftUserList = redisTemplate.opsForList().range(leftUserListKey, 0, -1);
        List<Object> orightUserList = redisTemplate.opsForList().range(rightUserListKey, 0, -1);

        for (Object o : oleftUserList) {
            String userNickname = (String) o;
            String userisReady = redisKeyUtil.isReadyKey(roomId, userNickname);
            redisTemplate.delete(userisReady);
        }

        for (Object o : orightUserList) {
            String userNickname = (String) o;
            String userisReady = redisKeyUtil.isReadyKey(roomId, userNickname);
            redisTemplate.delete(userisReady);
        }

        String phaseKey = redisKeyUtil.phaseKey(roomId);
        String phaseDetailKey = redisKeyUtil.phaseDetailKey(roomId);

        redisTemplate.opsForValue().set(phaseKey, 1);
        redisTemplate.opsForValue().set(phaseDetailKey, 0);

        // ?????? ?????? -> ????????? ?????????(JSON?????? ??????) -> ?????? ????????? ??????
        String roomChannel = redisChannelUtil.roomChannelKey(roomId);
        String debateStartMessage = redisMessageUtil.debateStartMessage(1, 0);
        redisPublisher.publishMessage(roomChannel, debateStartMessage);


        ScheduledExecutorService executorService = Executors.newScheduledThreadPool(1);

        String phaseStartTimeKey = redisKeyUtil.phaseStartTimeKey(roomId);
        Long serverTime = System.currentTimeMillis() / 1000L;
        redisTemplate.opsForValue().set(phaseStartTimeKey, serverTime);

        ScheduledFuture<?> future = executorService.schedule(new Runnable() {
            @Override
            public void run() {
                nextPhase(requestDebateStartDto.getRoomId());
            }
        }, 10, TimeUnit.SECONDS);
        // ???????????? 5???, ?????? ????????? 10???
        scheduledFutures.put(roomId + "_startWaitPhase", future);


    }

    // ??????????????? ?????? ??????????????? ???????????? ?????????????????????

    /**
     * ?????? ????????? ??????
     * ???????????????????????? ?????? ?????? ????????? ??????
     */
    public void nextPhase(Long roomId) {

        // ????????? ??????
        String phaseKey = redisKeyUtil.phaseKey(roomId);
        String phaseDetailKey = redisKeyUtil.phaseDetailKey(roomId);

        Integer previousPhase = (Integer) redisTemplate.opsForValue().get(phaseKey);
        Integer previousPhaseDetail = (Integer) redisTemplate.opsForValue().get(phaseDetailKey);
        Integer currentPhase = previousPhase;
        Integer currentPhaseDetail = previousPhaseDetail + 1;

        if (currentPhaseDetail == 5) {
            currentPhaseDetail = 1;
            currentPhase++;
        }
        redisTemplate.opsForValue().set(phaseKey, currentPhase);
        redisTemplate.opsForValue().set(phaseDetailKey, currentPhaseDetail);
        // ????????? ??????

        String phaseStartTimeKey = redisKeyUtil.phaseStartTimeKey(roomId);
        Long serverTime = System.currentTimeMillis() / 1000L;
        redisTemplate.opsForValue().set(phaseStartTimeKey, serverTime);

        switch (currentPhaseDetail) {
            case 1:
            case 2:
                speakPhase(roomId, currentPhase, currentPhaseDetail);
                break;
            case 3:
                votePhase(roomId, currentPhase, currentPhaseDetail);
                break;
            case 4:
                voteResultPhase(roomId, currentPhase, currentPhaseDetail);
                break;
        }


    }

    public void speakPhase(Long roomId, Integer currentPhase, Integer currentPhaseDetail) {
        String leftUserListKey = redisKeyUtil.leftUserListKey(roomId);
        String rightUserListKey = redisKeyUtil.rightUserListKey(roomId);
        List<Object> oleftUserList = redisTemplate.opsForList().range(leftUserListKey, 0, -1);
        List<Object> orightUserList = redisTemplate.opsForList().range(rightUserListKey, 0, -1);
        List<String> leftUserList = new ArrayList<>();
        List<String> rightUserList = new ArrayList<>();
        for (Object o : oleftUserList) {
            leftUserList.add((String) o);
        }
        for (Object o : orightUserList) {
            rightUserList.add((String) o);
        }

        String currentSpeakingUserNickname = "";
        String currentSpeakingTeam = "";
        if (currentPhaseDetail == 1) {
            currentSpeakingTeam = "LEFT";
            currentSpeakingUserNickname = leftUserList.get(currentPhase - 1);
        } else if (currentPhaseDetail == 2) {
            currentSpeakingTeam = "RIGHT";
            currentSpeakingUserNickname = rightUserList.get(currentPhase - 1);
        }


        String currentSpeakingUserKey = redisKeyUtil.currentSpeakingUserKey(roomId);
        String currentSpeakingTeamKey = redisKeyUtil.currentSpeakingTeamKey(roomId);

        redisTemplate.opsForValue().set(currentSpeakingUserKey, currentSpeakingUserNickname);
        redisTemplate.opsForValue().set(currentSpeakingTeamKey, currentSpeakingTeam);

        String roomChannelKey = redisChannelUtil.roomChannelKey(roomId);

        String phaseStartAllInOneMessage = redisMessageUtil.speakPhaseStartMessage(currentPhase, currentPhaseDetail, currentSpeakingTeam, currentSpeakingUserNickname);
        redisPublisher.publishMessage(roomChannelKey, phaseStartAllInOneMessage);

        ScheduledExecutorService executorService = Executors.newScheduledThreadPool(1);

        ScheduledFuture<?> future = executorService.schedule(new Runnable() {
            @Override
            public void run() {

                redisTemplate.opsForValue().set(currentSpeakingTeamKey, "");
                redisTemplate.opsForValue().set(currentSpeakingUserKey, "");

                nextPhase(roomId);
            }
        }, 180, TimeUnit.SECONDS);
        // ???????????? 10??? ?????? ????????? 180???
        scheduledFutures.put(roomId + "_speakingPhase", future);

    }

    public void skipPhase(RequestSkipDto requestSkipDto) {
        Long roomId = requestSkipDto.getRoomId();

        String currentSpeakingUserKey = redisKeyUtil.currentSpeakingUserKey(roomId);
        String currentSpeakingTeamKey = redisKeyUtil.currentSpeakingTeamKey(roomId);

        ScheduledFuture<?> future = scheduledFutures.get(roomId + "_speakingPhase");
        if (future != null) {
            future.cancel(false);
            redisTemplate.opsForValue().set(currentSpeakingTeamKey, "");
            redisTemplate.opsForValue().set(currentSpeakingUserKey, "");
            nextPhase(roomId);
        }

    }

    // ?????? ??????
    public void votePhase(Long roomId, Integer currentPhase, Integer currentPhaseDetail) {

        ScheduledExecutorService executorService = Executors.newScheduledThreadPool(1);

        String voteLeftKey = redisKeyUtil.voteLeftKey(roomId, currentPhase);
        String voteRightKey = redisKeyUtil.voteRightKey(roomId, currentPhase);

        redisTemplate.opsForValue().set(voteLeftKey, 0);
        redisTemplate.opsForValue().set(voteRightKey, 0);

        String roomChannelKey = redisChannelUtil.roomChannelKey(roomId);
        String voteStartMessage = redisMessageUtil.votePhaseStartMessage(currentPhase, currentPhaseDetail);

        redisPublisher.publishMessage(roomChannelKey, voteStartMessage);
        ScheduledFuture<?> future = executorService.schedule(new Runnable() {
            @Override
            public void run() {
                nextPhase(roomId);
                // ????????? ?????? ?????? ???????????? ?????????

            }
        }, 60, TimeUnit.SECONDS);
        // ???????????? 10??? ?????? 60???
        scheduledFutures.put(roomId + "_vote", future);

    }

    public void voteResultPhase(Long roomId, Integer currentPhase, Integer currentPhaseDetail) {


        String voteLeftKey = redisKeyUtil.voteLeftKey(roomId, currentPhase);
        String voteRightKey = redisKeyUtil.voteRightKey(roomId, currentPhase);
        Integer voteResultLeft = (Integer) redisTemplate.opsForValue().get(voteLeftKey);
        Integer voteResultRight = (Integer) redisTemplate.opsForValue().get(voteRightKey);
        Integer voteResultLeftPercent = 50;
        Integer voteResultRightPercent = 50;
        if (!(voteResultLeft == 0 && voteResultRight == 0)) {
            voteResultLeftPercent = ((Long) Math.round((double) ((voteResultLeft * 10000) / (voteResultLeft + voteResultRight)) / 100)).intValue();
            voteResultRightPercent = 100 - voteResultLeftPercent;
        }

        String voteLeftResulPercentKey = redisKeyUtil.voteLeftResulPercentKey(roomId, currentPhase);
        String voteRightResultPercentKey = redisKeyUtil.voteRightResultPercentKey(roomId, currentPhase);
        redisTemplate.opsForValue().set(voteLeftResulPercentKey, voteResultLeftPercent);
        redisTemplate.opsForValue().set(voteRightResultPercentKey, voteResultRightPercent);

        String roomChannelKey = redisChannelUtil.roomChannelKey(roomId);

        List<Integer> voteLeftResultList = new ArrayList<>();
        List<Integer> voteRightResultList = new ArrayList<>();
        if (currentPhase != 0 && currentPhaseDetail < 4) {
            for (int votePhase = 1; votePhase < currentPhase; votePhase++) {
                String curVoteLeftResulPercentKey = redisKeyUtil.voteLeftResulPercentKey(roomId, votePhase);
                String curVoteRightResultPercentKey = redisKeyUtil.voteRightResultPercentKey(roomId, votePhase);

                Integer voteLeftResult = (Integer) redisTemplate.opsForValue().get(curVoteLeftResulPercentKey);
                Integer voteRightResult = (Integer) redisTemplate.opsForValue().get(curVoteRightResultPercentKey);

                voteLeftResultList.add(voteLeftResult);
                voteRightResultList.add(voteRightResult);
            }
        } else if (currentPhase != 0 && currentPhaseDetail == 4) {
            for (int votePhase = 1; votePhase <= currentPhase; votePhase++) {
                String curVoteLeftResulPercentKey = redisKeyUtil.voteLeftResulPercentKey(roomId, votePhase);
                String curVoteRightResultPercentKey = redisKeyUtil.voteRightResultPercentKey(roomId, votePhase);

                Integer voteLeftResult = (Integer) redisTemplate.opsForValue().get(curVoteLeftResulPercentKey);
                Integer voteRightResult = (Integer) redisTemplate.opsForValue().get(curVoteRightResultPercentKey);

                voteLeftResultList.add(voteLeftResult);
                voteRightResultList.add(voteRightResult);
            }
        }

        String voteEndMessage = redisMessageUtil.voteEndMessage(currentPhase, currentPhaseDetail, voteLeftResultList, voteRightResultList);
        redisPublisher.publishMessage(roomChannelKey, voteEndMessage);

        if (currentPhase == 3) {
            ScheduledExecutorService executorService = Executors.newScheduledThreadPool(1);
            // ?????? ?????????
            // 3?????? ???????????? ??????????????? ?????? ??? ?????? ????????? ??????????????? Debate??? ?????? ?????????
            // ?????? DebateHistory??? ???????????? ????????? ???
            String leftUserListKey = redisKeyUtil.leftUserListKey(roomId);
            String rightUserListKey = redisKeyUtil.rightUserListKey(roomId);
            List<Object> leftuserlist = redisTemplate.opsForList().range(leftUserListKey, 0, -1);
            List<Object> rightuserlist = redisTemplate.opsForList().range(rightUserListKey, 0, -1);
            for (Object o : leftuserlist) {
                String userNickname = (String) o;
                DebateHistory debateHistory = debateHistoryService.createDebateHistory(roomId, userNickname, "LEFT");
                debateHistoryService.saveHistory(debateHistory);
            }
            for (Object o : rightuserlist) {
                String userNickname = (String) o;
                DebateHistory debateHistory = debateHistoryService.createDebateHistory(roomId, userNickname, "RIGHT");
                debateHistoryService.saveHistory(debateHistory);
            }
            // ?????? ???????????? ??? ?????? ???????????? ???????????? ????????? redis??? isDebateEnded key??? ?????? TRUE??? ?????? ???
            // ?????? ????????? ??? isDebateEnded??? ???????????? true??? ?????? DebateEndedException??? ????????? ???
            String debateEndedKey = redisKeyUtil.isDebateEndedKey(roomId);
            redisTemplate.opsForValue().set(debateEndedKey, "TRUE");

            /**
             * ??????????????? ?????? ????????? ?????????
             */
            ScheduledFuture<?> futureDebateEnd = executorService.schedule(new Runnable() {
                @Override
                public void run() {
                    // ????????? ????????? ?????? ??? ?????? ?????? ?????? Redis Pub/Sub??? ??????????????? ?????? ?????? ????????? ????????? ??????
                    // ???????????????????????? ??? ???????????? ?????? ?????????????????? ???????????? ??????????????????
                    String debateEndMessage = redisMessageUtil.debateEndMessage();
                    redisPublisher.publishMessage(roomChannelKey, debateEndMessage);
                }
            }, 30, TimeUnit.SECONDS);
            // ??????????????? 30???
            scheduledFutures.put(roomId + "_debateEnd", futureDebateEnd);

            ScheduledFuture<?> futureRemoveRoomInfos = executorService.schedule(new Runnable() {
                @Override
                public void run() {
                    /**
                     * ??? ????????? ?????? ????????? ???????????????
                     */
                    List<String> leftFileArr = getDeletingFileList(roomId, "LEFT");
                    List<String> rightFileArr = getDeletingFileList(roomId, "RIGHT");
                    try {
                        fileService.deleteFile(leftFileArr);
                        fileService.deleteFile(rightFileArr);
                    } catch (IOException e) {
                        e.printStackTrace();
                    }

                    // ?????? ?????????????????? ???????????? ????????? ??? ???????????? ?????? ?????? ??? ?????? ??????
                    List<String> keyList = new ArrayList<>();
                    keyList.add(redisKeyUtil.phaseKey(roomId));
                    keyList.add(redisKeyUtil.phaseDetailKey(roomId));
                    keyList.add(redisKeyUtil.phaseStartTimeKey(roomId));
                    keyList.add(redisKeyUtil.watchCntKey(roomId));
                    keyList.add(redisKeyUtil.leftUserListKey(roomId));
                    keyList.add(redisKeyUtil.rightUserListKey(roomId));
                    keyList.add(redisKeyUtil.currentSpeakingUserKey(roomId));
                    keyList.add(redisKeyUtil.currentSpeakingTeamKey(roomId));
                    keyList.add(redisKeyUtil.voteLeftKey(roomId, 1));
                    keyList.add(redisKeyUtil.voteLeftKey(roomId, 2));
                    keyList.add(redisKeyUtil.voteLeftKey(roomId, 3));
                    keyList.add(redisKeyUtil.voteRightKey(roomId, 1));
                    keyList.add(redisKeyUtil.voteRightKey(roomId, 2));
                    keyList.add(redisKeyUtil.voteRightKey(roomId, 3));
                    keyList.add(redisKeyUtil.voteLeftResulPercentKey(roomId, 1));
                    keyList.add(redisKeyUtil.voteLeftResulPercentKey(roomId, 2));
                    keyList.add(redisKeyUtil.voteLeftResulPercentKey(roomId, 3));
                    keyList.add(redisKeyUtil.voteRightResultPercentKey(roomId, 1));
                    keyList.add(redisKeyUtil.voteRightResultPercentKey(roomId, 2));
                    keyList.add(redisKeyUtil.voteRightResultPercentKey(roomId, 3));
                    keyList.add(redisKeyUtil.isDebateEndedKey(roomId));
                    keyList.add(redisKeyUtil.imgCardOpenedListKey(roomId, "LEFT"));
                    keyList.add(redisKeyUtil.imgCardOpenedListKey(roomId, "RIGHT"));
                    keyList.add(redisKeyUtil.debateStartTimeKey(roomId));
                    for (String key : keyList) {
                        redisTemplate.delete(key);
                    }
                    roomRepository.delete(roomRepository.findById(roomId).get());
                    publishService.unsubscribe(roomId.toString());
                }
            }, futureDebateEnd.getDelay(TimeUnit.SECONDS) + 10, TimeUnit.SECONDS);
            // ????????? 10??? ?????? ????????? 60???
            scheduledFutures.put(roomId + "_removeRoomInfo", futureRemoveRoomInfos);

        } else {
            ScheduledExecutorService executorService = Executors.newScheduledThreadPool(1);
            ScheduledFuture<?> futureDebateEnd = executorService.schedule(new Runnable() {
                @Override
                public void run() {
                    nextPhase(roomId);
                }
            }, 10, TimeUnit.SECONDS);
            // ????????? 10??? ?????? ????????? 10???
            scheduledFutures.put(roomId + "_voteResultEnd", futureDebateEnd);
        }

    }

    ;

    // ????????? ????????? ????????? ???????????????
    public void debateEndCreaterLeave(Long roomId) {

        // ?????? ???????????? ??? ?????? ???????????? ???????????? ????????? redis??? isDebateEnded key??? ?????? TRUE??? ?????? ???
        // ?????? ????????? ??? isDebateEnded??? ???????????? true??? ?????? DebateEndedException??? ????????? ???
        // ????????? ?????? ????????? ????????? ????????? ?????? ????????????
        String debateEndedKey = redisKeyUtil.isDebateEndedKey(roomId);
        if (((String) redisTemplate.opsForValue().get(debateEndedKey)).equals("TRUE")) {
            return;
        }
        String roomChannelKey = redisChannelUtil.roomChannelKey(roomId);
        redisTemplate.opsForValue().set(debateEndedKey, "TRUE");

        ScheduledExecutorService executorService = Executors.newScheduledThreadPool(1);


        ScheduledFuture<?> futureDebateEnd = executorService.schedule(new Runnable() {
            @Override
            public void run() {
                // ????????? ?????? ??? ??? ?????? ?????? ?????? Redis Pub/Sub??? ??????????????? ?????? ?????? ????????? ????????? ??????
                // ???????????????????????? ??? ???????????? ?????? ?????????????????? ???????????? ??????????????????
                String debateEndMessage = redisMessageUtil.debateEndMessage();
                redisPublisher.publishMessage(roomChannelKey, debateEndMessage);
            }
        }, 20, TimeUnit.SECONDS);
        scheduledFutures.put(roomId + "_debateEnd", futureDebateEnd);

        ScheduledFuture<?> futureRemoveRoomInfos = executorService.schedule(new Runnable() {
            @Override
            public void run() {
                // ?????? ?????????????????? ???????????? ????????? ??? ???????????? ?????? ?????? ??? ?????? ??????
                List<String> keyList = new ArrayList<>();
                keyList.add(redisKeyUtil.phaseKey(roomId));
                keyList.add(redisKeyUtil.phaseDetailKey(roomId));
                keyList.add(redisKeyUtil.phaseStartTimeKey(roomId));
                keyList.add(redisKeyUtil.watchCntKey(roomId));
                keyList.add(redisKeyUtil.leftUserListKey(roomId));
                keyList.add(redisKeyUtil.rightUserListKey(roomId));
                keyList.add(redisKeyUtil.debateStartTimeKey(roomId));
                List<Object> leftUserList = redisTemplate.opsForList().range(redisKeyUtil.leftUserListKey(roomId), 0, -1);
                List<Object> rightUserList = redisTemplate.opsForList().range(redisKeyUtil.rightUserListKey(roomId), 0, -1);
                for (Object o : leftUserList) {
                    String userNickname = (String) o;
                    keyList.add(redisKeyUtil.isReadyKey(roomId, userNickname));
                }
                for (Object o : rightUserList) {
                    String userNickname = (String) o;
                    keyList.add(redisKeyUtil.isReadyKey(roomId, userNickname));
                }

                keyList.add(redisKeyUtil.isDebateEndedKey(roomId));
                for (String key : keyList) {
                    redisTemplate.delete(key);
                }
                roomRepository.delete(roomRepository.findById(roomId).get());
                publishService.unsubscribe(roomId.toString());
            }
        }, futureDebateEnd.getDelay(TimeUnit.SECONDS) + 60, TimeUnit.SECONDS);
        // ????????? 10??? ?????? ????????? 60???
        scheduledFutures.put(roomId + "_removeRoomInfo", futureRemoveRoomInfos);

    }

    /**
     * ????????? ????????? ?????? redis??? ?????? name??? url??? ???????????? ????????? ????????? ??????????????? ???????????????
     * <p>
     * room:roomId ????????? ??????????????? name??? url??? ?????? ?????? ????????? ?????? ??????????????? ???????????????
     * <p>
     * cardOpenState??? ???????????? ?????? ?????? ??????????????? ready?????? ???????????????
     *
     * @param fileDtos
     */
    public void cardsUpload(Long roomId, String userNickname, List<FileDto> fileDtos) {

        String leftUserListKey = redisKeyUtil.leftUserListKey(roomId);
        String rightUserListKey = redisKeyUtil.rightUserListKey(roomId);

        List<Object> leftUserList = redisTemplate.opsForList().range(leftUserListKey, 0, -1);
        List<Object> rightUserList = redisTemplate.opsForList().range(rightUserListKey, 0, -1);

        for (int useridx = 0; useridx < leftUserList.size(); useridx++) {
            String curUserNick = (String) leftUserList.get(useridx);
            if (curUserNick.equals(userNickname)) {
                setFileInRedis(useridx, "LEFT", roomId, fileDtos);
            }
        }


        for (int useridx = 0; useridx < rightUserList.size(); useridx++) {
            String curUserNick = (String) rightUserList.get(useridx);
            if (curUserNick.equals(userNickname)) {
                setFileInRedis(useridx, "RIGHT", roomId, fileDtos);
            }
        }


    }

    private void setFileInRedis(int useridx, String team, Long roomId, List<FileDto> fileDtos) {

        String roomChannelKey = redisChannelUtil.roomChannelKey(roomId);

        for (int fileidx = 0; fileidx < fileDtos.size(); fileidx++) {
            FileDto fileDto = fileDtos.get(fileidx);
            String fileName = fileDto.getFileName();
            String fileUrl = fileDto.getFileUrl();
            System.out.println("fileidx" + fileidx);

            int curfileidx = (useridx * 2) + fileidx;
            System.out.println("curfileidx" + curfileidx);

            String imgCardNameKey = redisKeyUtil.imgCardNameKey(roomId, curfileidx, team);
            String imgCardUrlKey = redisKeyUtil.imgCardUrlKey(roomId, curfileidx, team);


            redisTemplate.opsForValue().set(imgCardNameKey, fileName);
            redisTemplate.opsForValue().set(imgCardUrlKey, fileUrl);


        }

    }

    public List<String> getDeletingFileList(Long roomId, String team) {
        List<String> fileList = new ArrayList<>();
        for (int i = 0; i < 6; i++) {
            String imgCardNameKey = redisKeyUtil.imgCardNameKey(roomId, i, team);
            String imgCardUrlKey = redisKeyUtil.imgCardUrlKey(roomId, i, team);
            Object o = redisTemplate.opsForValue().get(imgCardNameKey);
            if (o != null) {
                String o1 = (String) o;
                fileList.add(o1);
                System.out.println(o1);
                redisTemplate.delete(imgCardNameKey);
                redisTemplate.delete(imgCardUrlKey);
            }
        }

        return fileList;
    }

    public void cardOpen(String userNickname, int cardidx, Long roomId) {
        int useridx = -1;
        String team = "";

        String leftUserListKey = redisKeyUtil.leftUserListKey(roomId);
        List<Object> oleftUserList = redisTemplate.opsForList().range(leftUserListKey, 0, -1);
        for (int i = 0; i < 3; i++) {
            if (((String) oleftUserList.get(i)).equals(userNickname)) {
                useridx = i;
                team = "LEFT";
            }
        }

        String rightUserListKey = redisKeyUtil.rightUserListKey(roomId);
        List<Object> orightUserList = redisTemplate.opsForList().range(rightUserListKey, 0, -1);
        for (int i = 0; i < 3; i++) {
            if (((String) orightUserList.get(i)).equals(userNickname)) {
                useridx = i;
                team = "RIGHT";
            }
        }
        if (team.equals("")) {
            return;
        }

        int index = useridx * 2 + cardidx;
        String imgCardUrlKey = redisKeyUtil.imgCardUrlKey(roomId, index, team);
        String openedCardUrl = (String) redisTemplate.opsForValue().get(imgCardUrlKey);
        String openedCardListKey = redisKeyUtil.imgCardOpenedListKey(roomId, team);
        redisTemplate.opsForList().rightPush(openedCardListKey, openedCardUrl);
        List<Object> oOpenedCardList = redisTemplate.opsForList().range(openedCardListKey, 0, -1);

        String leftOpenedCardListKey = redisKeyUtil.imgCardOpenedListKey(roomId, "LEFT");
        String rightOpenedCardListKey = redisKeyUtil.imgCardOpenedListKey(roomId, "RIGHT");
        List<Object> oleftOpenedCardList = redisTemplate.opsForList().range(leftOpenedCardListKey, 0, -1);
        List<Object> orightOpenedCardList = redisTemplate.opsForList().range(rightOpenedCardListKey, 0, -1);
        List<String> leftOpenedCardList = new ArrayList<>();
        List<String> rightOpenedCardList = new ArrayList<>();
        for (Object o : oleftOpenedCardList) {
            leftOpenedCardList.add((String) o);
        }
        for (Object o : orightOpenedCardList) {
            rightOpenedCardList.add((String) o);
        }
        String imgCardOpenMessage = redisMessageUtil.imgCardOpenMessage(leftOpenedCardList, rightOpenedCardList);
        redisPublisher.publishMessage(redisChannelUtil.roomChannelKey(roomId), imgCardOpenMessage);
    }

    public void vote(RequestVoteDto requestVoteDto) {
        String phaseKey = redisKeyUtil.phaseKey(requestVoteDto.getRoomId());
        Integer phase = (Integer) redisTemplate.opsForValue().get(phaseKey);

        if (requestVoteDto.getVoteTeam().equals("LEFT")) {
            String voteLeftKey = redisKeyUtil.voteLeftKey(requestVoteDto.getRoomId(), phase);
            redisTemplate.opsForValue().increment(voteLeftKey);
        } else if (requestVoteDto.getVoteTeam().equals("RIGHT")) {
            String voteRightKey = redisKeyUtil.voteRightKey(requestVoteDto.getRoomId(), phase);
            redisTemplate.opsForValue().increment(voteRightKey);
        }
    }

    public String getUserRole(Long roomId, String userNickname) {
        Room room = roomRepository.findById(roomId).get();

        if (room.getRoom_creater_name().equals(userNickname)) {
            return "creater";
        }

        String leftUserListKey = redisKeyUtil.leftUserListKey(roomId);
        List<Object> oleftUserList = redisTemplate.opsForList().range(leftUserListKey, 0, -1);
        for (Object o : oleftUserList) {
            String curUser = (String) o;
            if (curUser.equals(userNickname)) {
                return "debater";
            }
        }
        String rightUserListKey = redisKeyUtil.rightUserListKey(roomId);
        List<Object> orightUserList = redisTemplate.opsForList().range(rightUserListKey, 0, -1);
        for (Object o : orightUserList) {
            String curUser = (String) o;
            if (curUser.equals(userNickname)) {
                return "debater";
            }
        }

        return "watcher";
    }


}
