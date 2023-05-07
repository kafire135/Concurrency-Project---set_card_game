package bguspl.set.ex;

import bguspl.set.Env;

import java.util.LinkedList;
import java.util.Vector;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

/**
 * This class manages the dealer's threads and data
 */
public class Dealer implements Runnable {

    /**
     * The game environment object.
     */
    private final Env env;

    /**
     * Game entities.
     */
    private final Table table;
    private final Player[] players;

    /**
     * The list of card ids that are left in the dealer's deck.
     */
    private final List<Integer> deck;

    /**
     * True iff game should be terminated.
     */
    private volatile boolean terminate;

    /**
     * The time when the dealer needs to reshuffle the deck due to turn timeout.
     */
    private long reshuffleTime = Long.MAX_VALUE;

    public Thread[] playersThreads;

    public Vector<Integer> setsQueue;

    private final long ONE_SEC= 1000;

    private long currentTime;

    private long timeOut;

    private long warning;

    private final long DEALER_SLEEP = 5;

    private final int PLAYER_QUEUE_MAX_LENGTH=3;

    public int[] cards;

    private int[] slots;

    public Dealer(Env env, Table table, Player[] players) {
        this.env = env;
        this.table = table;
        this.players = players;
        deck = IntStream.range(0, env.config.deckSize).boxed().collect(Collectors.toList());
        setsQueue = new Vector<Integer>();
        cards = new int[PLAYER_QUEUE_MAX_LENGTH];
        slots = new int[PLAYER_QUEUE_MAX_LENGTH];
        timeOut = env.config.turnTimeoutMillis;
        currentTime = timeOut;
        warning = env.config.turnTimeoutWarningMillis;
    }

    /**
     * The dealer thread starts here (main loop for the dealer thread).
     */
    @Override
    public void run() {
        env.logger.info("Thread " + Thread.currentThread().getName() + " starting.");
        playersThreads= new Thread[players.length];
        for (int i=0; i<playersThreads.length; i++){
            playersThreads[i] = new Thread(players[i]);
            playersThreads[i].start();
        }
        while (!shouldFinish()) {
            shuffle();
            placeAllCardsOnTable();
            timerLoop();
            removeAllCardsFromTable();
        }
        if(!terminate){
            announceWinners();
            terminate();
        }
        env.logger.info("Thread " + Thread.currentThread().getName() + " terminated.");
    }

    /**
     * The inner loop of the dealer thread that runs as long as the countdown did not time out.
     */
    private void timerLoop() {
        updateTimerDisplay(true);

        while (!terminate && System.currentTimeMillis() < reshuffleTime && setsOnTable()) {
            sleepUntilWokenOrTimeout();
        }
    }

    /**
     * Called when the game should be terminated.
     */
    public void terminate() {
        // TODO implement
        if(!terminate){
            for (int i= players.length-1; i>=0; i--) {
                players[i].terminate();
            }
            terminate=true;
            //Thread.currentThread().interrupt();
        }
    }

    /**
     * Check if the game should be terminated or the game end conditions are met.
     *
     * @return true iff the game should be finished.
     */
    private boolean shouldFinish() {
        return terminate || env.util.findSets(deck, 1).size() == 0;
    }

    private boolean setsOnTable(){
        return findSets().size()>0;
    }

    private List<int[]> findSets(){
        LinkedList<Integer> tableCards = new LinkedList<>();
        for (int i=0; i< table.slotToCard.length; i++){
            if (table.slotToCard[i]!=null){
                tableCards.add(table.slotToCard[i]);
            }
        }
        return env.util.findSets(tableCards, 1);
    }

    /**
     * Checks cards should be removed from the table and removes them.
     */
    private void removeCardsFromTable() {
        // TODO implement
        //if there are tokens on the slot- remove them

        for(int card : cards){
            table.inRemoveProgress[card] = true;
        }
        clearTokens();
        for (int i=0; i< cards.length; i++) {
            slots[i] = table.cardToSlot[cards[i]];
            table.removeCard(table.cardToSlot[cards[i]]);
        }
    }

    /**
     * Check if any cards can be removed from the deck and placed on the table.
     */
    private void placeCardsOnTable() {
        // TODO implement
        for(int i=0; i<slots.length; i++){
            if(deck.size()>0){
                int card = deck.remove(0);

                table.placeCard(card, slots[i]);
            }
        }
    }

    public void placeAllCardsOnTable(){
        // synchronized(table){
        for(int i=0; i<table.slotToCard.length; i++){
            if(deck.size()>0){
                int card = deck.remove(0);

                table.placeCard(card, i);
            }
        }
        // }
        for (Player player : players) {
            player.freeze = (player.send || player.counterPenalty>0);
        }
    }


    /**
     * Sleep for a fixed amount of time or until the thread is awakened for some purpose.
     */
    private void sleepUntilWokenOrTimeout() {
        // TODO implement
        while(currentTime>0 && !terminate && setsOnTable()){
            try {
                Thread.sleep(DEALER_SLEEP);
            } catch (InterruptedException ignored) {}
            updateTimerDisplay(false);
            //update point/penalty timer
            for (int id=0; id<players.length; id++) {
                synchronized(players[id]){
                    if(players[id].counterPenalty>0){
                        players[id].counterPenalty =  players[id].counterPenalty - DEALER_SLEEP;
                        if(players[id].counterPenalty % ONE_SEC == 0){
                            env.ui.setFreeze(id, players[id].counterPenalty);
                        }
                        if (players[id].counterPenalty==0){
                            players[id].freeze = false;
                            players[id].notifyAll();
                        }
                    }
                }
            }
            //check sets
            while(!setsQueue.isEmpty()){
                int id = setsQueue.remove(0);
                //players[id].send = false;
                if(players[id].actions.size()==PLAYER_QUEUE_MAX_LENGTH){
                    if(checkSet(id)){
                        players[id].counterPenalty = env.config.pointFreezeMillis;
                        env.ui.setFreeze(id, env.config.pointFreezeMillis);
                        players[id].send = false;
                        players[id].point();
                        removeCardsFromTable();
                        placeCardsOnTable();
                        updateTimerDisplay(true);
                    }
                    else{
                        players[id].counterPenalty = env.config.penaltyFreezeMillis;
                        env.ui.setFreeze(id, env.config.penaltyFreezeMillis);
                        players[id].send = false;
                    }
                    if(players[id].counterPenalty==0){
                        synchronized(players[id]) {
                            players[id].freeze = false;
                            players[id].notifyAll();
                        }
                    }
                }
                else{
                    synchronized(players[id]){
                        players[id].freeze = false;
                        players[id].send = false;
                        players[id].notifyAll();
                    }
                }
            }
        }
    }

    /**
     * Reset and/or update the countdown and the countdown display.
     */
    private void updateTimerDisplay(boolean reset) {
        // TODO implement
        if(reset){
            currentTime = timeOut;
            reshuffleTime = System.currentTimeMillis() + (currentTime);
            env.ui.setCountdown(currentTime, false);
        }
        else{
            currentTime = currentTime - DEALER_SLEEP;
            if(currentTime>=warning && (int)currentTime % ONE_SEC == 0){
                env.ui.setCountdown(currentTime, false);

            }
            else if (currentTime < warning){
                env.ui.setCountdown(currentTime, true);
            }
        }
    }

    /**
     * Returns all the cards from the table to the deck.
     */
    private void removeAllCardsFromTable() {
        // TODO implement
        //if there are tokens on the slot- remove them
        for (Player player : players) {
            player.freeze = true;
        }
        for (Player player : players) {
            synchronized(player.lockActions){player.actions.clear();
            }
        }
        env.ui.removeTokens();
        for(int i=0; i<table.slotToCard.length; i++){
            if(table.slotToCard[i]!=null){
                deck.add(table.slotToCard[i]);
                table.removeCard(i);
            }
        }
    }

    /**
     * Check who is/are the winner/s and displays them.
     */
    private void announceWinners() {
        // TODO implement
        LinkedList<Integer> winners = new LinkedList<Integer>();
        int max = 0;
        for (Player player : players) {
            if(player.score()>max){
                winners.clear();
                max = player.score();
                winners.add(player.id);
            }
            else if(player.score()==max){
                winners.add(player.id);
            }
        }
        int[] arrayWinners = new int[winners.size()];
        for(int i=0; i<arrayWinners.length; i++){
            arrayWinners[i] = winners.removeFirst();
        }
        env.ui.announceWinner(arrayWinners);
    }

    private void shuffle(){
        int size = deck.size();
        int[] tempDeck = new int[size];
        for(int i=0; i<size; i++){
            int index = (int)(Math.random()*deck.size());
            int card = deck.remove(index);
            tempDeck[i]=card;
        }
        for(int i=0; i<tempDeck.length; i++){
            deck.add(tempDeck[i]);
        }
    }

    private boolean checkSet(int id){
        Object[] temp = players[id].actions.toArray();
        for(int i=0; i<temp.length; i++){
            cards[i] = table.slotToCard[(int)temp[i]];
        }
        return env.util.testSet(cards);
    }

    public void clearTokens(){
        for(Player player : players){
            synchronized(player.lockActions){
                for (int card : cards){
                    if (player.actions.contains(table.cardToSlot[card])){
                        player.actions.removeElement(table.cardToSlot[card]);
                        table.removeToken(player.id, table.cardToSlot[card]);
                    }
                }
            }
        }
    }

}
