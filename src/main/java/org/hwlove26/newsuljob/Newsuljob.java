package org.hwlove26.newsuljob;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.attribute.Attribute;
import org.bukkit.boss.BarColor;
import org.bukkit.boss.BarStyle;
import org.bukkit.boss.BossBar;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.event.Listener;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scoreboard.Scoreboard;
import org.bukkit.scoreboard.Team;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;

public final class Newsuljob extends JavaPlugin implements Listener, CommandExecutor, TabCompleter {

    private Integer roundTime;
    private Team teamBoss;
    private Team teamPlayer;
    private int remainNum;
    private boolean bossWin;
    private ArrayList<Player> players;
    private Player boss;
    private boolean duringRound;

    @Override
    public void onEnable() {
        Scoreboard scoreboard = Bukkit.getScoreboardManager().getMainScoreboard();
        if (scoreboard.getTeam("boss") == null) {
            getLogger().warning("현재 boss(섬멸자) 팀이 설정 되지 않았습니다!");
        } else {
            teamBoss = scoreboard.getTeam("boss");
        }
        if (scoreboard.getTeam("player") == null) {
            getLogger().warning("현재 player(생존자) 팀이 설정 되지 않았습니다!");
        } else {
            teamPlayer = scoreboard.getTeam("player");
        }
        getCommand("설정").setExecutor(this);
        getCommand("디버그").setExecutor(this);
        getCommand("게임시작").setExecutor(this);
        saveDefaultConfig();
        roundTime = getConfig().getInt("roundTime");

    }

    @Override
    public void onDisable() {
        // Plugin shutdown logic
    }

    //메인 게임 로직
    public void mainGame() {
        Bukkit.broadcast(Component.text("게임 시작"));
        Scoreboard scoreboard = Bukkit.getScoreboardManager().getMainScoreboard();
        for (Player player : Bukkit.getOnlinePlayers()) {
            player.setGameMode(GameMode.ADVENTURE);
            player.getInventory().clear();
            Team currentTeam = scoreboard.getEntryTeam(player.getName());
            if (currentTeam != null) {
                currentTeam.removeEntry(player.getName());
            }
            player.getAttribute(Attribute.MAX_HEALTH).setBaseValue(20.0);
            remainNum = 0;
            bossWin = false;
        }
        players = new ArrayList<>();
        boss = getRandomPlayer();
        Bukkit.broadcast(Component.text("섬멸자는 " + boss.getName() + " 입니다").color(NamedTextColor.RED));
        boss.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, 9999999 * 20, 10));
        boss.addPotionEffect(new PotionEffect(PotionEffectType.NIGHT_VISION, 9999999 * 20, 255));
        boss.addPotionEffect(new PotionEffect(PotionEffectType.DARKNESS, 9999999 * 20, 1));
        teamBoss.addPlayer(boss);
        boss.getAttribute(Attribute.MAX_HEALTH).setBaseValue(80.0);
        boss.setHealth(boss.getMaxHealth());
        for (Player player : Bukkit.getOnlinePlayers()) {
            if (!player.equals(boss)) {
                teamPlayer.addPlayer(player);
                players.add(player);
                player.addPotionEffect(new PotionEffect(PotionEffectType.NIGHT_VISION, 9999999 * 20, 255));
                player.addPotionEffect(new PotionEffect(PotionEffectType.DARKNESS, 9999999 * 20, 1));
                player.setHealth(player.getMaxHealth());
                remainNum++;
            }
        }
        mainTimer();
    }

    public void mainTimer() {
        Scoreboard scoreboard = Bukkit.getScoreboardManager().getMainScoreboard();
        BossBar bossBar = Bukkit.createBossBar("섬멸자가 곧 풀려납니다 도망치세요!", BarColor.RED, BarStyle.SOLID);
        for (Player player : Bukkit.getOnlinePlayers()) {
            bossBar.addPlayer(player);
        }
        new BukkitRunnable() {
            int main_time = roundTime;
            int bosstime = 10;
            boolean firstrun = true;

            @Override
            public void run() {
                if (bosstime > 0) {
                    bossBar.setTitle("섬멸자가 나오기 까지: " + bosstime);
                    double progress = (double) bosstime / 10;
                    bossBar.setProgress(progress);
                    bosstime -= 1;
                } else {
                    if (firstrun) {
                        boss.removePotionEffect(PotionEffectType.SLOWNESS);
                        Bukkit.broadcast(Component.text("섬멸자가 풀려났습니다"));
                        firstrun = false;
                    }
                    if (main_time <= 0) {
                        if (bossWin) {
                            bossBar.setTitle("섬멸자 승리!");
                            Bukkit.broadcast(Component.text("섬멸자가 승리했습니다"));
                            bossBar.setProgress(0);
                        } else {
                            bossBar.setTitle("생존자 승리!");
                            Bukkit.broadcast(Component.text("생존자가 승리했습니다"));
                            bossBar.setProgress(0);
                        }
                        boss.getAttribute(Attribute.MAX_HEALTH).setBaseValue(20.0);
                        boss = null;
                        players = new ArrayList<>();
                        for  (Player player : Bukkit.getOnlinePlayers()) {
                            player.setGameMode(GameMode.ADVENTURE);
                            for (PotionEffect effect : player.getActivePotionEffects()) {
                                player.removePotionEffect(effect.getType());
                            }
                            player.getInventory().clear();
                            Team currentTeam = scoreboard.getEntryTeam(player.getName());
                            if (currentTeam != null) {
                                currentTeam.removeEntry(player.getName());
                            }
                            player.getAttribute(Attribute.MAX_HEALTH).setBaseValue(20.0);
                        }
                        bossBar.removeAll();
                        duringRound = false;
                        cancel();
                    }
                    bossBar.setTitle("⏳ 남은 시간: " + main_time + "초");
                    double progress = (double) main_time / roundTime;
                    bossBar.setProgress(progress);
                    main_time--;
                }
            }
        }.runTaskTimer(this, 0L, 20L);
    }

    public Player getRandomPlayer() {
        ArrayList<Player> players = new ArrayList<>();
        players.addAll(Bukkit.getOnlinePlayers());
        Collections.shuffle(players);
        if (players.isEmpty()) {
            return null;
        }
        Random rand = new Random();
        int i = rand.nextInt(players.size());
        return players.get(i);
    }

    public void initSetting() {
        Scoreboard scoreboard = Bukkit.getScoreboardManager().getMainScoreboard();
        if (scoreboard.getTeam("boss") == null) {
            teamBoss = scoreboard.registerNewTeam("boss");
            teamBoss.displayName(Component.text("boss"));
            teamBoss.prefix(Component.text("[섬멸자]"));
            teamBoss.color(NamedTextColor.RED);
        } else{
            teamBoss = scoreboard.getTeam("boss");
        }
        if (scoreboard.getTeam("player") == null) {
            Team player = scoreboard.registerNewTeam("player");
            player.displayName(Component.text("player"));
            player.prefix(Component.text("[생존자]"));
            player.color(NamedTextColor.BLUE);
        } else {
            teamPlayer = scoreboard.getTeam("player");
        }
    }

    public void viewSetting() {
        Bukkit.broadcast(Component.text("현재 세팅:"));
        Bukkit.broadcast(Component.text("라운드 시간 :" +  roundTime));
    }


    //디버그용
    public void viewAllVar() {
        Bukkit.broadcast(Component.text("roundTime: " + roundTime));
        Bukkit.broadcast(Component.text("remainNum: " + remainNum));
        Bukkit.broadcast(Component.text("playerWin: " + bossWin));
        Bukkit.broadcast(Component.text("boss: " + boss.getName()));
    }

    public void changeRoundTime(Integer time) {
        roundTime = time;
        getConfig().set("roundTime", roundTime);
        saveConfig();
    }


    //퍼미션 체크
    public void notOP(Player p) {
        p.sendMessage(Component.text("OP 아님"));
    }


    //커멘드
    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (sender instanceof Player) {
            Player player = (Player) sender;
            if (command.getName().equalsIgnoreCase("설정")) {
                if (player.isOp()) {
                    if (args.length == 1) {
                        if (args[0].equalsIgnoreCase("초기설정")) {
                            initSetting();
                            player.sendMessage(Component.text("초기 설정 완료!"));
                            player.sendMessage(Component.text("라운드 시간 :" + roundTime));
                        }
                        if (args[0].equalsIgnoreCase("보기")) {
                            viewSetting();
                        }
                    }
                    if (args.length == 2) {
                        if (args[0].equalsIgnoreCase("시간")) {
                            Integer time = 270;
                            try {
                                time = Integer.parseInt(args[1]);
                            } catch (NumberFormatException e) {
                                e.printStackTrace();
                                sender.sendMessage(Component.text("정수가 아니라 자동으로 270초로 설정됨"));
                            }
                            changeRoundTime(time);
                            player.sendMessage(Component.text("시간 설정 완료 :" + roundTime));

                        }
                    }
                } else {
                    notOP(player);
                }
            }

            if (command.getName().equalsIgnoreCase("디버그")) {
                if (player.isOp()) {
                    if (args.length == 1) {
                        if (args[0].equalsIgnoreCase("변수")) {
                            viewAllVar();
                        }
                    }
                } else {
                    notOP(player);
                }
            }

            if (command.getName().equalsIgnoreCase("게임시작")) {
                if (player.isOp()) {
                    if (!duringRound) {
                        mainGame();
                        duringRound = true;
                    } else {
                        player.sendMessage("게임이 이미 진행중 입니다!");
                    }
                }
            }
        }

        return true;
    }
    //오토탭
    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        List<String> completions = new ArrayList<>();

        if(command.getName().equalsIgnoreCase("설정")) {
            if (args.length == 1) {
                completions.add("초기설정");
                completions.add("시간");
                completions.add("보기");
            }
        }
        if (command.getName().equalsIgnoreCase("디버그")) {
            if (args.length == 1) {
                completions.add("변수");
            }
        }
        return  completions;
    }

}
