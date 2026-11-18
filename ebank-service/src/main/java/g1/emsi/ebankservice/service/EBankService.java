package g1.emsi.ebankservice.service;

import g1.emsi.ebankservice.entities.BankAccount;
import g1.emsi.ebankservice.feign.CustomerRestClient;
import g1.emsi.ebankservice.model.Customer;
import g1.emsi.ebankservice.repository.BankAccountRepository;
import lombok.AllArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Date;
import java.util.List;
import java.util.UUID;

@Service
@Transactional
@AllArgsConstructor
public class EBankService {
    private BankAccountRepository bankAccountRepository;
    private CustomerRestClient customerRestClient;

    public List<BankAccount> getAllBankAccounts() {
        return bankAccountRepository.findAll();
    }

    public BankAccount getBankAccountById(String id) {
        BankAccount bankAccount = bankAccountRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Account not found"));
        Customer customer = customerRestClient.getCustomerById(bankAccount.getCustomerId());
        bankAccount.setCustomer(customer);
        return bankAccount;
    }

    public BankAccount saveAccount(BankAccount bankAccount) {
        try {
            Customer customer = customerRestClient.getCustomerById(bankAccount.getCustomerId());
            bankAccount.setId(UUID.randomUUID().toString());
            bankAccount.setCreatedAt(new Date());
            BankAccount savedAccount = bankAccountRepository.save(bankAccount);
            savedAccount.setCustomer(customer);
            return savedAccount;
        } catch (Exception e) {
            throw new RuntimeException(e.getMessage());
        }
    }
}
