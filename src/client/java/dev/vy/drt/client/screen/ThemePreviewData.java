package dev.vy.drt.client.screen;

import dev.vy.drt.config.*;
import java.util.*;

/** Fictional records confined to a preview screen. Never passed to persistence or tracking. */
final class ThemePreviewData {
    final List<DungeonRunRecord> chests = new ArrayList<>();
    final List<DungeonRunCompletionRecord> runs = new ArrayList<>();
    ThemePreviewData() {
        for(int i=0;i<12;i++) {
            String id="theme-preview-"+i;
            long time=System.currentTimeMillis()-i*900000L;
            String floor=i%3==0?"K5":i%3==1?"F7":"M7";
            var run=new DungeonRunCompletionRecord(time,"DUNGEON",floor,i%2==0?"S+":"S",240000+i*9000,id,id);
            run.completionId=id; run.runTimeSource=DungeonRunCompletionRecord.RUN_TIME_OFFICIAL;
            run.score=300+i; run.scoreRecorded=true;
            runs.add(run);
            var chest=new DungeonRunRecord(time,i+1,i+1,floor,run.grade,"Bedrock Chest",1000000,1500000+i*300000,500000+i*300000,
                List.of(new DungeonLootEntry("Wither Essence","ESSENCE_WITHER",120),new DungeonLootEntry("Recombobulator 3000","RECOMBOBULATOR_3000",1)));
            chest.recordId=id; chest.runSessionId=id; chest.linkedRunId=id; chest.runLinkSource=DungeonRunRecord.LINK_LIVE;
            chests.add(chest);
        }
    }
}
