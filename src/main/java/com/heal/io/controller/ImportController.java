package com.heal.io.controller;

import com.heal.io.service.MedicineCsvImportService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
@RequestMapping("/import")
@RequiredArgsConstructor
public class ImportController {

    private final MedicineCsvImportService importService;

    @GetMapping("/medicine")
    public String showImportPage(Model model) {
        return "import/medicine";
    }

    @PostMapping("/medicine")
    public String importMedicine(
            @RequestParam("file") MultipartFile file,
            RedirectAttributes redirectAttributes) {
        
        if (file.isEmpty()) {
            redirectAttributes.addFlashAttribute("error", "Please select a file to upload");
            return "redirect:/import/medicine";
        }
        
        if (!file.getOriginalFilename().endsWith(".csv")) {
            redirectAttributes.addFlashAttribute("error", "Please upload a CSV file");
            return "redirect:/import/medicine";
        }
        
        try {
            MedicineCsvImportService.ImportResult result = importService.importMedicines(file);
            
            if (result.isSuccess()) {
                redirectAttributes.addFlashAttribute("success", 
                    result.getMessage() + ". " + 
                    (result.getSkipped().isEmpty() ? "" : 
                     "Skipped: " + result.getSkipped().size() + " rows"));
                
                if (!result.getSkipped().isEmpty() && result.getSkipped().size() <= 10) {
                    redirectAttributes.addFlashAttribute("skippedDetails", result.getSkipped());
                }
            } else {
                redirectAttributes.addFlashAttribute("error", result.getError());
            }
        } catch (MaxUploadSizeExceededException e) {
            redirectAttributes.addFlashAttribute("error", 
                "File size exceeds the maximum allowed size (50MB). Please use a smaller file or split the CSV into multiple files.");
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("error", "Error importing file: " + e.getMessage());
        }
        
        return "redirect:/import/medicine";
    }
    
    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public String handleMaxSizeException(MaxUploadSizeExceededException ex, RedirectAttributes redirectAttributes) {
        redirectAttributes.addFlashAttribute("error", 
            "File size exceeds the maximum allowed size (50MB). Please use a smaller file or split the CSV into multiple files.");
        return "redirect:/import/medicine";
    }
}

