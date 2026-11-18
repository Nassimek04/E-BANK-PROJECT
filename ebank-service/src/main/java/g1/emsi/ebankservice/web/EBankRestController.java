package g1.emsi.ebankservice.web;

import g1.emsi.ebankservice.entities.BankAccount;
import g1.emsi.ebankservice.service.EBankService;
import lombok.AllArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@AllArgsConstructor
public class EBankRestController {
    private EBankService eBankService;

    @GetMapping("/accounts")
    public List<BankAccount> getAllBankAccounts() {
        return eBankService.getAllBankAccounts();
    }

    @GetMapping("/accounts/{id}")
    public BankAccount getBankAccountById(@PathVariable String id) {
        return eBankService.getBankAccountById(id);
    }

    @PostMapping("/accounts")
    public BankAccount saveAccount(@RequestBody BankAccount bankAccount) {
        return eBankService.saveAccount(bankAccount);
    }
}
