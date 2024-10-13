package com.example.wordguess;

import jakarta.servlet.http.HttpSession;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Controller
public class UserController {

    @Autowired
    private WordRepository wordRepository;

    @GetMapping("/dashboard")
    public String showForm(HttpSession session, Model model) {
        User user = (User) session.getAttribute("user");
        String winMessage = (String) session.getAttribute("win_message");

        if (user != null) {
            model.addAttribute("user", user);
            model.addAttribute("levels", new String[]{"Easy", "Medium", "Hard"});
            model.addAttribute("selectedLevel", "");
            model.addAttribute("message", winMessage);
            return "word-form";
        } else {
            return "redirect:/";
        }
    }

    @PostMapping("/word")
    public String getWord(@ModelAttribute("selectedLevel") String selectedLevel, Model model, HttpSession session) {
        User user = (User) session.getAttribute("user");
        session.setAttribute("win_message", null);

        if (user != null) {
            // Get the list of seen words for this level
            Map<String, List<String>> seenWordsMap = (Map<String, List<String>>) session.getAttribute("seenWordsMap");
            if (seenWordsMap == null) {
                seenWordsMap = new HashMap<>();  // Initialize the map if it doesn't exist
            }

            List<String> seenWords = seenWordsMap.getOrDefault(selectedLevel, new ArrayList<>());

            // Fetch all words for the level
            List<Word> allWordsForLevel = wordRepository.findAllWordsByLevel(selectedLevel);

            // Check if all words are seen
            if (seenWords.size() >= allWordsForLevel.size()) {
                model.addAttribute("message", "Congratulations! You've completed all words for this level.");
                return "redirect:/dashboard";  // Redirect if all words are seen
            }

            Word word = null;

            // Find a new word that hasn't been seen yet
            List<Word> availableWords = new ArrayList<>(allWordsForLevel);
            availableWords.removeIf(w -> seenWords.contains(w.getWordName())); // Remove seen words from the list

            if (!availableWords.isEmpty()) {
                word = availableWords.get((int) (Math.random() * availableWords.size())); // Select a random unseen word
            }

            // If all words have been seen
            if (word == null) {
                model.addAttribute("message", "No more words available for this level.");
                return "redirect:/dashboard";  // Redirect if no words are found
            }

            // Add the word to the list of seen words
            seenWords.add(word.getWordName());
            seenWordsMap.put(selectedLevel, seenWords);
            session.setAttribute("seenWordsMap", seenWordsMap);  // Store the updated map in session

            model.addAttribute("word", word);
            session.setAttribute("word", word);
            session.setAttribute("attempts", 0);  // Reset attempts for the new word
            model.addAttribute("user", user);

            return "redirect:/showWord";
        } else {
            return "redirect:/";
        }
    }

    @GetMapping("/showWord")
    public String showWord(HttpSession session, Model model) {
        User user = (User) session.getAttribute("user");

        if (user != null) {
            Word word = (Word) session.getAttribute("word");
            model.addAttribute("GivenHints", word.getHints());
            model.addAttribute("GivenImage", word.getImage());
            model.addAttribute("user", user);
            return "word-input";
        } else {
            return "redirect:/";
        }
    }

    @PostMapping("/getWord")
    public String login(@RequestParam String word, HttpSession session, Model model) {
        User user = (User) session.getAttribute("user");

        if (user != null) {
            Word wordFromSession = (Word) session.getAttribute("word");
            Integer attempts = (Integer) session.getAttribute("attempts");
            String selectedLevel = wordFromSession.getLevel();  // Store the level

            model.addAttribute("GivenHints", wordFromSession.getHints());
            model.addAttribute("GivenImage", wordFromSession.getImage());
            model.addAttribute("score", user.getScore());  // Always add the score to the model

            // Handle correct guess
            if (word != null && wordFromSession.getWordName().equalsIgnoreCase(word)) {
                // Correct guess: increase score and fetch the next word
                int newScore = user.getScore().intValue() + 10;  // Casting the score to int
                user.setScore((long) newScore);  // Update the user's score
                session.setAttribute("user", user);  // Store the updated user back in session
                model.addAttribute("score", newScore);  // Add score to the model

                // Fetch the next word (using updated seen words logic)
                return getNextWordAfterCorrect(session, model, selectedLevel);

            } else {
                // Handle incorrect guess
                attempts++;
                session.setAttribute("attempts", attempts);

                if (attempts >= 3) {
                    // Add the current word to the seen words list after 3 failed attempts
                    Map<String, List<String>> seenWordsMap = (Map<String, List<String>>) session.getAttribute("seenWordsMap");
                    List<String> seenWords = seenWordsMap.getOrDefault(selectedLevel, new ArrayList<>());
                    if (!seenWords.contains(wordFromSession.getWordName())) {
                        seenWords.add(wordFromSession.getWordName());
                        seenWordsMap.put(selectedLevel, seenWords);
                        session.setAttribute("seenWordsMap", seenWordsMap);
                    }

                    // Fetch the next word after 3 failed attempts
                    return getNextWordAfterFailed(session, model, selectedLevel);
                } else {
                    // Incorrect guess but still within allowed attempts
                    model.addAttribute("message", "Incorrect guess! Attempts left: " + (3 - attempts));
                    return "word-input";
                }
            }

        } else {
            return "redirect:/";
        }
    }

    private String getNextWordAfterCorrect(HttpSession session, Model model, String selectedLevel) {
        Map<String, List<String>> seenWordsMap = (Map<String, List<String>>) session.getAttribute("seenWordsMap");
        List<String> seenWords = seenWordsMap.getOrDefault(selectedLevel, new ArrayList<>());

        // Fetch all words for the level
        List<Word> allWordsForLevel = wordRepository.findAllWordsByLevel(selectedLevel);
        List<Word> availableWords = new ArrayList<>(allWordsForLevel);
        availableWords.removeIf(w -> seenWords.contains(w.getWordName())); // Remove seen words from the list

        if (availableWords.isEmpty()) {
            model.addAttribute("message", "Congratulations! You've completed all words for this level.");
            return "redirect:/dashboard";  // Redirect if all words are seen
        }

        Word nextWord = availableWords.get((int) (Math.random() * availableWords.size())); // Select a random unseen word

        session.setAttribute("word", nextWord);  // Set the new word in the session
        model.addAttribute("GivenHints", nextWord.getHints());
        model.addAttribute("GivenImage", nextWord.getImage());
        model.addAttribute("message", "Congratulations! You got it right. Here's the next word.");
        return "word-input";  // Stay on the same page with the new word
    }

    private String getNextWordAfterFailed(HttpSession session, Model model, String selectedLevel) {
        Map<String, List<String>> seenWordsMap = (Map<String, List<String>>) session.getAttribute("seenWordsMap");
        List<String> seenWords = seenWordsMap.getOrDefault(selectedLevel, new ArrayList<>());

        // Fetch all words for the level
        List<Word> allWordsForLevel = wordRepository.findAllWordsByLevel(selectedLevel);
        List<Word> availableWords = new ArrayList<>(allWordsForLevel);
        availableWords.removeIf(w -> seenWords.contains(w.getWordName())); // Remove seen words from the list

        if (availableWords.isEmpty()) {
            model.addAttribute("message", "Congratulations! You've completed all words for this level.");
            return "redirect:/dashboard";  // Redirect if all words are seen
        }

        Word nextWord = availableWords.get((int) (Math.random() * availableWords.size())); // Select a random unseen word

        session.setAttribute("word", nextWord);
        session.setAttribute("attempts", 0);  // Reset attempts for the next word
        model.addAttribute("message", "Sorry! You lost. Here's the next word.");
        model.addAttribute("GivenHints", nextWord.getHints());
        model.addAttribute("GivenImage", nextWord.getImage());
        return "word-input";
    }

    @PostMapping("/logout")
    public String logout(HttpSession session) {
        session.invalidate(); // Invalidate the session
        return "redirect:/"; // Redirect to the home page or login page
    }
}
