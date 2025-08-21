import logo from './logo.svg';
import './App.css';
import {BrowserRouter, Route, Routes} from "react-router-dom";
import Home from "./pages/Home";
import SignUp from "./pages/users/SignUp";
import UserList from "./pages/users/UserList";
import ProductCreate from "./pages/products/ProductCreate";
import ProductList from "./pages/products/ProductList";
import OrderCreate from "./pages/orders/OrderCreate";
import OrderList from "./pages/orders/OrderList";
import PaymentSuccess from "./pages/orders/PaymentSuccess";

function App() {
  return (
    <BrowserRouter>
      <Routes>
        <Route path="/" element={<Home />} />

        <Route path="/users/signup" element={<SignUp />} />
        <Route path="/users/list" element={<UserList />} />

        <Route path="/products/create" element={<ProductCreate />} />
        <Route path="/products/list" element={<ProductList />} />

        <Route path="/orders/create" element={<OrderCreate />} />
        <Route path="/orders/list" element={<OrderList />} />

        <Route path="/payment-success" element={<PaymentSuccess />} />


      </Routes>
    </BrowserRouter>
  );
}

export default App;
