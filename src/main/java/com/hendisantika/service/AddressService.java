package com.hendisantika.service;

import com.hendisantika.model.Address;
import com.hendisantika.model.User;
import com.hendisantika.repository.AddressRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class AddressService {

    private final AddressRepository addressRepository;

    public List<Address> findByUser(User user) {
        return addressRepository.findByUser(user);
    }

    public List<Address> findByUserId(Long userId) {
        return addressRepository.findByUserId(userId);
    }

    public Optional<Address> findById(Long id) {
        return addressRepository.findById(id);
    }

    @Transactional
    public Address save(Address address) {
        return addressRepository.save(address);
    }

    @Transactional
    public void delete(Long id) {
        addressRepository.deleteById(id);
    }
}
