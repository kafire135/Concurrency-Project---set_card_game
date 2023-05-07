package bguspl.set.ex;

import bguspl.set.Config;
import bguspl.set.Env;
import bguspl.set.UserInterface;
import bguspl.set.Util;
import bguspl.set.ex.TableTest.MockLogger;
import bguspl.set.ex.TableTest.MockUserInterface;
import bguspl.set.ex.TableTest.MockUtil;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.Properties;

public class DealerTest {

    Dealer dealer;
    Player[] players;
    Table table;
    Env env;
    @Mock
    private UserInterface ui;

    @BeforeEach
    void setUp() {
        
        Properties properties = new Properties();
        MockLogger logger = new MockLogger();
        Config config = new Config(logger, properties);

        players = new Player[1];
        env = new Env(logger, config, new MockUserInterface(), new MockUtil());
        table = new Table(env);
        dealer = new Dealer(env, table, players);
        players[0] = new Player(env, dealer, table, 0, true);

    }

    @Test
    void clearAllTokens(){
        table.placeCard(0, 0);
        table.placeCard(1, 1);
        table.placeCard(2, 2);
        players[0].actions.add(0);
        players[0].actions.add(1);
        players[0].actions.add(2);
        table.placeToken(0, 0);
        table.placeToken(0, 1);
        table.placeToken(0, 2);
        dealer.cards[0] = 0;
        dealer.cards[1] = 1;
        dealer.cards[2] = 2;
        dealer.clearTokens();
        int expectedSize = 0;
        assertEquals(expectedSize, players[0].actions.size());
    }

    @Test
    void clearSomeTokens(){
        table.placeCard(0, 0);
        table.placeCard(1, 1);
        table.placeCard(2, 2);
        table.placeCard(3, 3);
        players[0].actions.add(0);
        players[0].actions.add(1);
        players[0].actions.add(2);
        table.placeToken(0, 0);
        table.placeToken(0, 1);
        table.placeToken(0, 2);
        dealer.cards[0] = 0;
        dealer.cards[1] = 1;
        dealer.cards[2] = 3;
        dealer.clearTokens();
        int expectedSize = 1;
        int expectedSlot = 2;
        assertEquals(expectedSize, players[0].actions.size());
        assertEquals(expectedSlot, players[0].actions.get(0));
    }




    
}
