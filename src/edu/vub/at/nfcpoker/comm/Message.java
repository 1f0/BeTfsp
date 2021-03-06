/*
 * wePoker: Play poker with your friends, wherever you are!
 * Copyright (C) 2012, The AmbientTalk team.
 *
 * This program is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License as published by the
 * Free Software Foundation; either version 2 of the License, or (at your
 * option) any later version.
 *
 * This program is distributed in the hope that it will be useful, but
 * WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU General
 * Public License for more details.
 *
 * You should have received a copy of the GNU General Public License along
 * with this program; if not, write to the Free Software Foundation, Inc.,
 * 59 Temple Place, Suite 330, Boston, MA 02111-1307 USA
 */

package edu.vub.at.nfcpoker.comm;

import java.util.Date;
import java.util.Iterator;
import java.util.List;
import java.util.UUID;

import com.esotericsoftware.minlog.Log;

import edu.vub.at.commlib.Future;
import edu.vub.at.nfcpoker.Card;
import edu.vub.at.nfcpoker.PokerGameState;
import edu.vub.at.nfcpoker.PlayerState;
import edu.vub.at.nfcpoker.Hand;

public interface Message {

	enum ClientActionType { Bet, Fold, Check, AllIn, // From Client
							Unknown };               // For Server: 'undecided'

	public static final class ClientAction {
		public ClientActionType actionType;
		public int roundMoney;
		public int extraMoney;
		public transient boolean handled; // 

		public ClientAction(ClientActionType actionType) {
			this(actionType, 0, 0);
		}
		public ClientAction(ClientActionType actionType, int roundMoney, int extraMoney) {
			this.actionType = actionType;
			this.roundMoney = roundMoney;
			this.extraMoney = extraMoney;
			this.handled    = false;
		}

		// for kryo
		public ClientAction() {
			this.handled = false;
		}

		@Override
		public String toString() {
			switch (actionType) {
			case Fold: case Check:
				return actionType.toString();
			case Bet: case AllIn:
				return actionType.toString() + "(" + roundMoney + " / " + extraMoney + ")";
			default:
				Log.warn("wePoker - Message", "Unsupported action type");
				return actionType.toString() + "(" + roundMoney + " / " + extraMoney + ")";
			}
		}		
	}

	public static abstract class TimestampedMessage implements Message {
		public long timestamp;

		public TimestampedMessage() {
			timestamp = new Date().getTime();
		}

		@Override
		public String toString() {
			return this.getClass().getSimpleName() + "@" + timestamp;
		}
	}

	public static final class StateChangeMessage extends TimestampedMessage {
		public PokerGameState newState;

		public StateChangeMessage(PokerGameState newState_) {
			newState = newState_;
		}

		// for kryo
		public StateChangeMessage() {}

		@Override
		public String toString() {
			return super.toString() + ": State change to " + newState;
		}
	}

	public static class ReceiveHoleCardsMessage extends TimestampedMessage {
		public Card card1, card2;

		public ReceiveHoleCardsMessage(Card one, Card two) {
			card1 = one;
			card2 = two;
		}

		//kryo
		public ReceiveHoleCardsMessage() {}

		@Override
		public String toString() {
			return super.toString() + ": Receive cards [" + card1 + ", " + card2 + "]";
		}
	}

	public static class ReceivePublicCards extends TimestampedMessage {
		public Card[] cards;

		public ReceivePublicCards(Card[] cards_) {
			cards = cards_;
		}

		//kryo
		public ReceivePublicCards() {}

		@Override
		public String toString() {
			StringBuilder cardsStr = new StringBuilder(": Receive cards [");
			cardsStr.append(cards[0].toString());
			for (int i = 1; i < cards.length; i++)
				cardsStr.append(", ").append(cards[i].toString());

			return super.toString() + cardsStr.toString() + "]";
		}
	}

	public static class FutureMessage extends TimestampedMessage {
		public UUID futureId;
		public Object futureValue;

		public FutureMessage(UUID futureId_, Object futureValue_) {
			futureId = futureId_;
			futureValue = futureValue_;
		}

		// kryo
		public FutureMessage() {}

		@Override
		public String toString() {
			return super.toString() + ": Resolve " + futureId + " with " + futureValue;
		}
	}

	public static class RequestClientActionFutureMessage extends TimestampedMessage {
		public UUID futureId;
		public int round;

		public RequestClientActionFutureMessage(Future<?> f, int round_) {
			futureId = f.getFutureId();
			round = round_;
		}

		// kryo
		public RequestClientActionFutureMessage() {}

		@Override
		public String toString() {
			return super.toString() + ": Future message for " + futureId + ". Round: " + round + ".";
		}
	}

	public static class ClientActionMessage extends TimestampedMessage {

		public int userId;
		public ClientAction action;

		public ClientActionMessage(ClientAction action, int id) {
			this.action = action;
			this.userId = id;
		}

		// kryo
		public ClientActionMessage() {}

		public ClientAction getClientAction(){
			return action;
		}

		@Override
		public String toString() {
			return super.toString() + ": Client action information message, client" + userId + " -> " + action.toString();
		}
	}


	public class RoundWinnersDeclarationMessage extends TimestampedMessage implements Message {

		public List<PlayerState> bestPlayers;
		public List<String> bestPlayerNames;
		public boolean showCards;
		public Hand bestHand;
		public int chips;

		public RoundWinnersDeclarationMessage(List<PlayerState> bestPlayers, List<String> bestNames, boolean showCards, Hand bestHand, int amountOfChips) {
			this.bestPlayers = bestPlayers;
			this.bestPlayerNames = bestNames;
			this.showCards = showCards;
			this.bestHand = bestHand;
			this.chips = amountOfChips;
		}

		// kryo
		public RoundWinnersDeclarationMessage() {}

		@Override
		public String toString() {
			return super.toString() + ": Round winners" + this.bestPlayers.toString();
		}

		public String winMessageString() {
			String s = "\u20AC" + chips + " chips won by ";
			Iterator<String> playersIt = bestPlayerNames.iterator();
			while (playersIt.hasNext()) {
				s = s + " - " + playersIt.next();
			}
			return s;
		}
	}

	public static class ToastMessage extends TimestampedMessage {

		public String message;

		public ToastMessage(String message) {
			this.message = message;
		}

		// kryo
		public ToastMessage() {}

		@Override
		public String toString() {
			return super.toString() + ": Client toast information message -> " + message;
		}
	}

	public static class CheatMessage extends TimestampedMessage {

		public String nickname;
		public int amount;

		public CheatMessage(String nickname, int amount) {
			this.nickname = nickname;
			this.amount = amount;
		}

		// kryo
		public CheatMessage() {}

		@Override
		public String toString() {
			return super.toString() + ": Client cheat information message," +
					"nickname -> " + nickname +
					"amount -> " + amount;
		}
	}

	public static class TableButtonsMessage extends TimestampedMessage {

		public int bigAmount;
		public int bigId;
		public int smallAmount;
		public int smallId;
		public int dealerId;

		
		public TableButtonsMessage(int dealerId, int smallId, int smallAmount, int bigId, int bigAmount) {
			this.dealerId = dealerId;
			this.smallId = smallId;
			this.smallAmount = smallAmount;
			this.bigId = bigId;
			this.bigAmount = bigAmount;
		}

		// kryo
		public TableButtonsMessage() {}
		
		@Override
		public String toString() {
			return String.format("%s: dealer=%d; small(%d)=%d; big(%d)=%d",
					super.toString(), dealerId, smallId, smallAmount, bigId, bigAmount);
		}
	}

	public static class PoolMessage extends TimestampedMessage {

		public int poolMoney;

		public PoolMessage(int poolMoney) {
			this.poolMoney = poolMoney;
		}

		// kryo
		public PoolMessage() {}

		@Override
		public String toString() {
			return super.toString() + ": Client pool money information message -> " + poolMoney;
		}
	}

	public class SetIDMessage extends TimestampedMessage implements Message {

		public int id;

		public SetIDMessage(Integer id) {
			this.id = id;
		}

		// kryo
		public SetIDMessage() {}

		@Override
		public String toString() {
			return super.toString() + ": set ID to " + this.id;
		}
	}

	public static class SetClientParameterMessage extends TimestampedMessage {

		public int clientId;
		public boolean reconnect;
		public String nickname;
		public int avatar;
		public int money;

		public SetClientParameterMessage(int clientId, boolean reconnect, String nickname, int avatar, int money) {
			this.clientId = clientId;
			this.reconnect = reconnect;
			this.nickname = nickname;
			this.avatar = avatar;
			this.money = money;
		}

		// kryo
		public SetClientParameterMessage() {}

		@Override
		public String toString() {
			return super.toString() + ": Client parameter information message, "+
					"clientId -> " + clientId + ", " +
					"reconnect -> " + reconnect + ", "+
					"nickname -> " + nickname + ", "+
					"avatar -> " + avatar + ", "+
					"money -> " + money;
		}
	}
	

	public static class SetNicknameMessage extends TimestampedMessage {
		public String nickname;

		public SetNicknameMessage(String nickname) {
			this.nickname = nickname;
		}
		
		// kryo
		public SetNicknameMessage() {}

		@Override
		public String toString() {
			return super.toString() + ": Set nickname to " + nickname;
		}
	}
	
	public class ResetMessage extends TimestampedMessage implements Message {
		// kryo
		public ResetMessage() {}
		
		@Override
		public String toString() {
			return super.toString() + ": Reset game";
		}
	}

}
