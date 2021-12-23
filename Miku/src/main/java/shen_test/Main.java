package shen_test;

import static net.dv8tion.jda.api.requests.GatewayIntent.GUILD_MESSAGES;
import static net.dv8tion.jda.api.requests.GatewayIntent.GUILD_VOICE_STATES;

import java.util.HashMap;
import java.util.Map;

import com.sedmelluq.discord.lavaplayer.player.AudioLoadResultHandler;
import com.sedmelluq.discord.lavaplayer.player.AudioPlayerManager;
import com.sedmelluq.discord.lavaplayer.player.DefaultAudioPlayerManager;
import com.sedmelluq.discord.lavaplayer.source.AudioSourceManagers;
import com.sedmelluq.discord.lavaplayer.tools.FriendlyException;
import com.sedmelluq.discord.lavaplayer.track.AudioPlaylist;
import com.sedmelluq.discord.lavaplayer.track.AudioTrack;

import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.JDABuilder;
import net.dv8tion.jda.api.OnlineStatus;
import net.dv8tion.jda.api.entities.Activity;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.MessageChannel;
import net.dv8tion.jda.api.entities.TextChannel;
import net.dv8tion.jda.api.entities.VoiceChannel;
import net.dv8tion.jda.api.events.message.MessageReceivedEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import net.dv8tion.jda.api.managers.AudioManager;

import java.util.*;
import java.util.function.Supplier;

public class Main extends ListenerAdapter {

	GuildMusicManager musicManager;

	public static void main(String[] args) throws Exception {
		JDA jda = JDABuilder.create("OTIyMTAzOTY0MzExMzg4MjIx.Yb8mPA.6kCYQi62EeEWtnmT7JNRFuj9yPs", GUILD_MESSAGES,
				GUILD_VOICE_STATES).addEventListeners(new Main())
				.setStatus(OnlineStatus.ONLINE)
				.setActivity(Activity.playing("type '~info' to start"))
				.build();
	}

	private final AudioPlayerManager playerManager;
	private final Map<Long, GuildMusicManager> musicManagers;

	private Main() {
		this.musicManagers = new HashMap<>();

		this.playerManager = new DefaultAudioPlayerManager();
		AudioSourceManagers.registerRemoteSources(playerManager);
		AudioSourceManagers.registerLocalSource(playerManager);

	}

	private synchronized GuildMusicManager getGuildAudioPlayer(Guild guild) {
		long guildId = Long.parseLong(guild.getId());
		GuildMusicManager musicManager = musicManagers.get(guildId);

		if (musicManager == null) {
			musicManager = new GuildMusicManager(playerManager);
			musicManagers.put(guildId, musicManager);
		}

		guild.getAudioManager().setSendingHandler(musicManager.getSendHandler());

		return musicManager;
	}

	public static VoiceChannel firstchannel;

	@Override
	public void onMessageReceived(MessageReceivedEvent event) {
		String[] command = event.getMessage().getContentRaw().split(" ", 2);
		
		// play
		if ("~play".equals(command[0]) && command.length == 2) {
			loadAndPlay(event.getTextChannel(), command[1]);
			firstchannel = (VoiceChannel) event.getMember().getVoiceState().getChannel();
		}
		
		// pause
		else if ("~pause".equals(command[0])) {
			AudioPlayerSendHandler.audioPlayer.setPaused(true);
		}
		// resume
		else if ("~resume".equals(command[0])) {
			AudioPlayerSendHandler.audioPlayer.setPaused(false);
		}
		// skip
		else if ("~skip".equals(command[0])) {
			skipTrack(event.getTextChannel());
		}
		// queue
		else if ("~queue".equals(command[0])) {
			long count = 1;
			for (AudioTrack audio : musicManager.scheduler.queue) {
				event.getTextChannel().sendMessage(String.valueOf(count) + ". " + audio.getInfo().title).queue();
				count += 1;
			}
		}
		// remove // Wait to improve multiple remove
		else if ("~rm".equals(command[0])) {
			AudioTrack targetAudio = (AudioTrack) musicManager.scheduler.queue.toArray()[1];
			for (AudioTrack audio : musicManager.scheduler.queue) {
				if (audio.getPosition() == Integer.parseInt(command[1])) {
					targetAudio = audio;
				}
			}
			musicManager.scheduler.queue.remove(targetAudio);
		}
		// insert // Wait to write
		else if ("~insert".equals(command[0])&& command.length == 2) {
			loadAndINSERT(event.getTextChannel(), command[1]);
		}
		// clear
		else if ("~clear".equals(command[0])) {
			musicManager.scheduler.queue.clear();
		}
		// move
		else if ("~move".equals(command[0])) {
			AudioManager audioManager = event.getGuild().getAudioManager();
			VoiceChannel channel = (VoiceChannel) event.getMember().getVoiceState().getChannel();
			audioManager.openAudioConnection(channel);
		}
		// disconnect
		else if ("~disconnect".equals(command[0])) {
			event.getGuild().getAudioManager().closeAudioConnection();
		}
		// info
		else if ("~info".equals(command[0])) {
        	EmbedBuilder info = new EmbedBuilder();
        	info.setTitle("Music Bot");
        	info.setDescription("$: Change by user input\n"
        			+ "~play $URL : play song or add to queue, pls input clean URL\n"
        			+ "Ex: https://www.youtube.com/watch?v=xauTD6nRMio&list=PLNXFvhG3th5eqHGO_GpjImT9zQBOWvO7g&index=7 (X) \n"
        			+ "should be : https://www.youtube.com/watch?v=xauTD6nRMio \n"
        			+ "~pause\n"
        			+ "~resume\n"
        			+ "~skip\n"
        			+ "~queue : show song in queue \n"
        			+ "~remove $position(integer) : remove track from queue by position \n"
        			+ "~insert : insert track to the first of queue \n"
        			+ "~clear : clear the queue\n"
        			+ "~move : move the bot to which voice channel that user connected \n"
        			+ "~disconnect : disconnect the bot \n"
        			+ "Wait To Improve : \n"
        			+ "1. insert track to specific position \n"
        			+ "2. multiple remove \n"
        			+ "3. automatically seperate the playlist"
        			);
        	
        	
        	info.setFooter("create by shenchris");
        	MessageChannel channel = event.getChannel();
        	channel.sendTyping().queue();
        	channel.sendMessageEmbeds(info.build()).queue();
		}

		super.onMessageReceived(event);
	}

	private void loadAndPlay(final TextChannel channel, final String trackUrl) {
		musicManager = getGuildAudioPlayer(channel.getGuild());

		playerManager.loadItemOrdered(musicManager, trackUrl, new AudioLoadResultHandler() {
			@Override
			public void trackLoaded(AudioTrack track) {
				channel.sendMessage("Adding to queue " + track.getInfo().title).queue();

				play(channel.getGuild(), musicManager, track);
			}

			@Override
			public void playlistLoaded(AudioPlaylist playlist) {
				AudioTrack firstTrack = playlist.getSelectedTrack();

				if (firstTrack == null) {
					firstTrack = playlist.getTracks().get(0);
				}

				channel.sendMessage("Adding to queue " + firstTrack.getInfo().title + " (first track of playlist "
						+ playlist.getName() + ")").queue();

				play(channel.getGuild(), musicManager, firstTrack);
			}

			@Override
			public void noMatches() {
				channel.sendMessage("Nothing found by " + trackUrl).queue();
			}

			@Override
			public void loadFailed(FriendlyException exception) {
				channel.sendMessage("Could not play: " + exception.getMessage()).queue();
			}
		});
	}

	private void loadAndINSERT(final TextChannel channel, final String trackUrl) {
		musicManager = getGuildAudioPlayer(channel.getGuild());

		playerManager.loadItemOrdered(musicManager, trackUrl, new AudioLoadResultHandler() {
			@Override
			public void trackLoaded(AudioTrack track) {
				channel.sendMessage("Adding to queue " + track.getInfo().title).queue();

				insert(channel.getGuild(), musicManager, track);
			}

			@Override
			public void playlistLoaded(AudioPlaylist playlist) {
				AudioTrack firstTrack = playlist.getSelectedTrack();

				if (firstTrack == null) {
					firstTrack = playlist.getTracks().get(0);
				}

				channel.sendMessage("Adding to queue " + firstTrack.getInfo().title + " (first track of playlist "
						+ playlist.getName() + ")").queue();

				insert(channel.getGuild(), musicManager, firstTrack);
			}

			@Override
			public void noMatches() {
				channel.sendMessage("Nothing found by " + trackUrl).queue();
			}

			@Override
			public void loadFailed(FriendlyException exception) {
				channel.sendMessage("Could not play: " + exception.getMessage()).queue();
			}
		});
	}
	
	private void play(Guild guild, GuildMusicManager musicManager, AudioTrack track) {
		connectToFirstVoiceChannel(guild.getAudioManager());
		musicManager.scheduler.queue(track);
	}
	
	private void insert(Guild guild, GuildMusicManager musicManager, AudioTrack track) {
		musicManager.scheduler.insert(track);
	}
	
	private void skipTrack(TextChannel channel) {
		GuildMusicManager musicManager = getGuildAudioPlayer(channel.getGuild());
		musicManager.scheduler.nextTrack();

		channel.sendMessage("Skipped to next track.").queue();
	}

	private static void connectToFirstVoiceChannel(AudioManager audioManager) {
		if (!audioManager.isConnected()) {
			audioManager.openAudioConnection(firstchannel);
		}
	}

}