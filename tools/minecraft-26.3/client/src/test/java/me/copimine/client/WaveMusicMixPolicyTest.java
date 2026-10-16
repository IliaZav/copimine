package me.copimine.client;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class WaveMusicMixPolicyTest {
    @Test void OnlyTheCurrentWaveFiveMusicIsDucked() {
        assertEquals(.24F,WaveMusicMixPolicy.volume("copimine","end_rift/wave_5",true,.8F,.3F),.00001F);
        assertEquals(.8F,WaveMusicMixPolicy.volume("minecraft","music.overworld",true,.8F,.3F));
        assertEquals(.8F,WaveMusicMixPolicy.volume("copimine","end_rift/wave_4",true,.8F,.3F));
        assertEquals(.8F,WaveMusicMixPolicy.volume("copimine","end_rift/wave_5",false,.8F,.3F));
        assertEquals(0,WaveMusicMixPolicy.volume("copimine","end_rift/wave_5",true,0,.3F));
    }
    @Test void DuckAndRestoreAreFiniteAndDoNotRewindTheTrack() {
        float factor=1;
        for(int tick=0;tick<16;tick++)factor=WaveMusicMixPolicy.advance(factor,true);
        assertEquals(.3F,factor,.00001F);
        for(int tick=0;tick<60;tick++)factor=WaveMusicMixPolicy.advance(factor,true);
        assertEquals(.3F,factor,.00001F);
        for(int tick=0;tick<24;tick++)factor=WaveMusicMixPolicy.advance(factor,false);
        assertEquals(1,factor,.00001F);
    }
    @Test void InvalidFactorsCannotRaiseTheUsersVolume() {
        assertEquals(.8F,WaveMusicMixPolicy.volume("copimine","end_rift/wave_5",true,.8F,Float.NaN));
        assertEquals(.8F,WaveMusicMixPolicy.volume("copimine","end_rift/wave_5",true,.8F,100));
        assertEquals(.24F,WaveMusicMixPolicy.volume("copimine","end_rift/wave_5",true,.8F,-1),.00001F);
    }
}
